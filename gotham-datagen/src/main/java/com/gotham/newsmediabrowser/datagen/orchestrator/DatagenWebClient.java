package com.gotham.newsmediabrowser.datagen.orchestrator;

import com.gotham.newsmediabrowser.datagen.client.DatagenClientException;
import com.gotham.newsmediabrowser.datagen.client.GeneratedMedia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP client communicating exclusively with {@code gotham-web} CRUD endpoints
 * ({@code POST /journalist}, {@code GET /journalist}, {@code POST /article}).
 */
public class DatagenWebClient {

    private static final String SERVICE_NAME = "GothamWeb";
    private static final Pattern JOURNALIST_DELETE_FORM = Pattern.compile("/journalist/([A-Za-z0-9_-]+)/delete");
    private static final Pattern ARTICLE_DELETE_FORM = Pattern.compile("/article/([A-Za-z0-9_-]+)/delete");

    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    public DatagenWebClient(String baseUrl, Duration timeout, HttpClient httpClient) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(60);
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DatagenWebClient(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(60), null);
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/health/elasticsearch"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public String createJournalist(String firstName, String lastName, String email, String bio) {
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(email, "email must not be null");

        try {
            String formBody = "firstName=" + urlEncode(firstName)
                    + "&lastName=" + urlEncode(lastName)
                    + "&email=" + urlEncode(email)
                    + "&bio=" + urlEncode(bio != null ? bio : "");

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/journalist"))
                    .timeout(timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "text/html, */*")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> postResp = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
            int code = postResp.statusCode();
            if (code != 302 && code != 200 && code != 201) {
                throw new DatagenClientException(SERVICE_NAME, code, "Failed to create journalist " + email + ": " + postResp.body());
            }

            // Capture the newest ES ID from /journalist list page
            return captureNewestJournalistId();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DatagenClientException(SERVICE_NAME, "Failed to POST /journalist at " + baseUrl, e);
        }
    }

    public List<String> listJournalistIds() {
        try {
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/journalist?size=100"))
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new DatagenClientException(SERVICE_NAME, resp.statusCode(), "Failed to list journalists");
            }

            return extractIds(resp.body(), JOURNALIST_DELETE_FORM);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DatagenClientException(SERVICE_NAME, "Failed to GET /journalist at " + baseUrl, e);
        }
    }

    public String createArticle(ArticlePayload payload, List<GeneratedMedia> mediaFiles) {
        Objects.requireNonNull(payload, "payload must not be null");

        try {
            String boundary = "----DatagenBoundary" + UUID.randomUUID().toString().replace("-", "");
            byte[] multipartBytes = buildMultipartBody(boundary, payload, mediaFiles != null ? mediaFiles : List.of());

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/article"))
                    .timeout(timeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "text/html, */*")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBytes))
                    .build();

            HttpResponse<String> postResp = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
            int code = postResp.statusCode();
            if (code != 302 && code != 200 && code != 201) {
                throw new DatagenClientException(SERVICE_NAME, code, "Failed to create article '" + payload.title() + "': " + postResp.body());
            }

            return "OK";
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DatagenClientException(SERVICE_NAME, "Failed to POST /article multipart at " + baseUrl, e);
        }
    }

    private String captureNewestJournalistId() throws IOException, InterruptedException {
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/journalist"))
                .timeout(timeout)
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new DatagenClientException(SERVICE_NAME, resp.statusCode(), "Failed to read /journalist after creation");
        }

        Matcher matcher = JOURNALIST_DELETE_FORM.matcher(resp.body());
        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new DatagenClientException(SERVICE_NAME, "Could not extract newest journalist ID from /journalist response");
    }

    /**
     * Newest-first article ids scraped from the list page delete forms (same contract as
     * {@link #listJournalistIds()}). Used by integration tests to clean up a small run.
     */
    public List<String> listArticleIds() {
        try {
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/article?size=100"))
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new DatagenClientException(SERVICE_NAME, resp.statusCode(), "Failed to list articles");
            }
            return extractIds(resp.body(), ARTICLE_DELETE_FORM);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DatagenClientException(SERVICE_NAME, "Failed to GET /article at " + baseUrl, e);
        }
    }

    /**
     * {@code POST /journalist/{id}/delete} — cascade-strips nested bylines then deletes the master.
     * Treats 404 as already-gone (idempotent cleanup).
     */
    public void deleteJournalist(String journalistId) {
        postDelete("/journalist/" + journalistId + "/delete", "journalist " + journalistId);
    }

    /**
     * {@code POST /article/{id}/delete} — removes GCS objects then the ES document.
     * Treats 404 as already-gone (idempotent cleanup).
     */
    public void deleteArticle(String articleId) {
        postDelete("/article/" + articleId + "/delete", "article " + articleId);
    }

    private void postDelete(String path, String label) {
        try {
            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "text/html, */*")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> resp = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code == 302 || code == 200 || code == 404) {
                return;
            }
            throw new DatagenClientException(SERVICE_NAME, code, "Failed to delete " + label);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DatagenClientException(SERVICE_NAME, "Failed to POST " + path + " at " + baseUrl, e);
        }
    }

    private static List<String> extractIds(String html, Pattern deleteForm) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = deleteForm.matcher(html != null ? html : "");
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private byte[] buildMultipartBody(String boundary, ArticlePayload payload, List<GeneratedMedia> mediaFiles) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] lineSep = "\r\n".getBytes(StandardCharsets.UTF_8);

        addTextField(out, boundary, "title", payload.title());
        if (payload.subtitle() != null && !payload.subtitle().isBlank()) {
            addTextField(out, boundary, "subtitle", payload.subtitle());
        }
        addTextField(out, boundary, "summary", payload.summary());
        addTextField(out, boundary, "body", payload.body());
        addTextField(out, boundary, "status", payload.status());
        addTextField(out, boundary, "language", payload.language());
        addTextField(out, boundary, "section", payload.section());
        addTextField(out, boundary, "tags", payload.tags() != null ? payload.tags() : "");
        addTextField(out, boundary, "location", payload.location());
        addTextField(out, boundary, "source", payload.source());

        if (payload.seoTitle() != null && !payload.seoTitle().isBlank()) {
            addTextField(out, boundary, "seoTitle", payload.seoTitle());
        }
        if (payload.seoDescription() != null && !payload.seoDescription().isBlank()) {
            addTextField(out, boundary, "seoDescription", payload.seoDescription());
        }
        if (payload.seoKeywords() != null && !payload.seoKeywords().isBlank()) {
            addTextField(out, boundary, "seoKeywords", payload.seoKeywords());
        }

        // Bylines
        int order = 1;
        for (String jId : payload.journalistIds()) {
            addTextField(out, boundary, "journalistIds", jId);
            addTextField(out, boundary, "bylineOrder[" + jId + "]", String.valueOf(order));
            addTextField(out, boundary, "role[" + jId + "]", order == 1 ? "AUTHOR" : "CO_AUTHOR");
            order++;
        }

        // Binary media files
        for (GeneratedMedia media : mediaFiles) {
            addBinaryField(out, boundary, "mediaFiles", media.filename(), media.mimeType(), media.bytes());
        }

        // Final boundary closing
        out.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        out.write(lineSep);

        return out.toByteArray();
    }

    private void addTextField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value != null ? value : "").getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void addBinaryField(ByteArrayOutputStream out, String boundary, String name, String filename, String mimeType, byte[] data) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizeBaseUrl(String url) {
        Objects.requireNonNull(url, "baseUrl must not be null");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
