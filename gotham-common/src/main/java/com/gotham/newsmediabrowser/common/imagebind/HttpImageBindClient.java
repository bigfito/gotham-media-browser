package com.gotham.newsmediabrowser.common.imagebind;

import com.gotham.newsmediabrowser.common.config.ImageBindProperties;
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.media.MediaType;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls the real {@code imagebind-service} over HTTP: {@code POST {base-url}/embed/{modality}}.
 * Text is sent as JSON; media is sent as {@code multipart/form-data} under the field {@code file}.
 * Every response is expected to be {@code {"embedding":[ … 1024 floats … ]}}.
 *
 * <p>Transport failures and non-2xx responses surface as a {@link DependencyException} (HTTP 503)
 * that names the service without leaking the endpoint or any detail.
 */
public class HttpImageBindClient implements ImageBindClient {

    private static final Logger log = LoggerFactory.getLogger(HttpImageBindClient.class);
    private static final String SERVICE = "ImageBind";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration requestTimeout;

    public HttpImageBindClient(HttpClient httpClient, ImageBindProperties properties) {
        this.httpClient = httpClient;
        this.baseUrl = properties.baseUrl().replaceAll("/+$", "");
        this.requestTimeout = properties.requestTimeout();
    }

    @Override
    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        String json = Json.createObjectBuilder().add("text", text).build().toString();
        HttpRequest request = baseRequest("/embed/text")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    @Override
    public float[] embedMedia(MediaType type, byte[] data, String filename, String contentType) {
        if (type == null || data == null || data.length == 0) {
            throw new IllegalArgumentException("media type and non-empty data are required");
        }
        String modality = type.name().toLowerCase(Locale.ROOT);
        String boundary = "gotham-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, data, filename, contentType);
        HttpRequest request = baseRequest("/embed/" + modality)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.ofByteArray(body))
                .build();
        return send(request);
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(requestTimeout);
    }

    private float[] send(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                log.warn("ImageBind returned HTTP {} for {}", status, request.uri().getPath());
                throw new DependencyException(SERVICE, new IOException("HTTP " + status));
            }
            return parseEmbedding(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyException(SERVICE, e);
        } catch (IOException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /** Extracts the {@code embedding} array and validates its length. */
    private float[] parseEmbedding(byte[] body) {
        JsonArray array;
        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(body))) {
            JsonObject object = reader.readObject();
            array = object.getJsonArray("embedding");
        } catch (RuntimeException e) {
            throw new DependencyException(SERVICE, new IOException("Malformed embedding response", e));
        }
        if (array == null || array.size() != EMBEDDING_DIM) {
            int size = array == null ? 0 : array.size();
            throw new DependencyException(SERVICE,
                    new IOException("Expected " + EMBEDDING_DIM + " dims, got " + size));
        }
        float[] embedding = new float[EMBEDDING_DIM];
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            embedding[i] = (float) array.getJsonNumber(i).doubleValue();
        }
        return embedding;
    }

    private byte[] multipartBody(String boundary, byte[] data, String filename, String contentType) {
        String safeName = (filename == null || filename.isBlank()) ? "file" : filename;
        String mime = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";

        ByteArrayOutputStream out = new ByteArrayOutputStream(header.length() + data.length + footer.length());
        try {
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(data);
            out.write(footer.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // ByteArrayOutputStream never throws; rethrow defensively as unchecked.
            throw new IllegalStateException("Failed to assemble multipart body", e);
        }
        return out.toByteArray();
    }
}
