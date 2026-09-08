package com.gotham.newsmediabrowser.datagen.orchestrator;

import com.gotham.newsmediabrowser.datagen.client.DatagenClientException;
import com.gotham.newsmediabrowser.datagen.client.GeneratedMedia;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatagenWebClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void isHealthy_whenElasticHealthReturns200_returnsTrue() {
        server.createContext("/api/health/elasticsearch", exchange -> {
            byte[] resp = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        DatagenWebClient client = new DatagenWebClient(baseUrl);
        assertThat(client.isHealthy()).isTrue();
    }

    @Test
    void isHealthy_whenDown_returnsFalse() {
        DatagenWebClient client = new DatagenWebClient("http://localhost:1");
        assertThat(client.isHealthy()).isFalse();
    }

    @Test
    void createJournalist_success_capturesNewestId() {
        server.createContext("/journalist", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Location", "/journalist");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                // GET /journalist returns HTML list
                String html = """
                        <html>
                        <body>
                          <table>
                            <tr>
                              <td>
                                <form action="/journalist/es-journalist-id-999/delete" method="post">
                                  <button type="submit">Delete</button>
                                </form>
                              </td>
                            </tr>
                          </table>
                        </body>
                        </html>
                        """;
                byte[] resp = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            }
        });

        DatagenWebClient client = new DatagenWebClient(baseUrl);
        String id = client.createJournalist("Vicki", "Vale", "vicki@gothamgazette.com", "Senior reporter");

        assertThat(id).isEqualTo("es-journalist-id-999");
    }

    @Test
    void listJournalistIds_extractsFromDeleteForm() {
        server.createContext("/journalist", exchange -> {
            String html = """
                    <html>
                    <body>
                      <form action="/journalist/id-1/delete" method="post"></form>
                      <form action="/journalist/id-2/delete" method="post"></form>
                    </body>
                    </html>
                    """;
            byte[] resp = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        DatagenWebClient client = new DatagenWebClient(baseUrl);
        List<String> ids = client.listJournalistIds();

        assertThat(ids).containsExactly("id-1", "id-2");
    }

    @Test
    void createArticle_success_multipartPayload() {
        AtomicReference<String> receivedBody = new AtomicReference<>();

        server.createContext("/article", exchange -> {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            receivedBody.set(new String(bytes, StandardCharsets.UTF_8));

            exchange.getResponseHeaders().set("Location", "/article");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        DatagenWebClient client = new DatagenWebClient(baseUrl);
        ArticlePayload payload = new ArticlePayload(
                "City Council Passes Transit Reform",
                "Subway funding",
                "Summary text",
                "Body paragraph 1\nBody paragraph 2",
                "PUBLISHED",
                "en",
                "Politics",
                "transit,reform",
                "Gotham City Hall",
                "Gotham Gazette",
                "City Council Transit",
                "Summary SEO",
                "transit,reform",
                List.of("id-1", "id-2"),
                "Photo prompt",
                "Audio script",
                "Video prompt"
        );

        GeneratedMedia media = new GeneratedMedia(
                "MOCK_PNG_DATA".getBytes(StandardCharsets.UTF_8),
                "image/png",
                "photo.png",
                "Council Photo",
                "Press photo",
                "Description",
                "Alt text",
                "Credit"
        );

        String result = client.createArticle(payload, List.of(media));

        assertThat(result).isEqualTo("OK");
        assertThat(receivedBody.get()).contains("name=\"title\"");
        assertThat(receivedBody.get()).contains("City Council Passes Transit Reform");
        assertThat(receivedBody.get()).contains("name=\"journalistIds\"");
        assertThat(receivedBody.get()).contains("id-1");
        assertThat(receivedBody.get()).contains("name=\"mediaFiles\"; filename=\"photo.png\"");
        assertThat(receivedBody.get()).contains("MOCK_PNG_DATA");
    }

    @Test
    void createArticle_errorResponse_throwsDatagenClientException() {
        server.createContext("/article", exchange -> {
            byte[] err = "Validation error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, err.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(err);
            }
        });

        DatagenWebClient client = new DatagenWebClient(baseUrl);
        ArticlePayload payload = new ArticlePayload(
                "Bad Article", "", "Sum", "Body", "PUBLISHED", "en", "Politics", "", "Gotham", "Source",
                "", "", "", List.of("id-1"), "", "", ""
        );

        assertThatThrownBy(() -> client.createArticle(payload, List.of()))
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("HTTP 400");
    }
}
