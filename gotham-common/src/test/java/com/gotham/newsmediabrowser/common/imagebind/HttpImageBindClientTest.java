package com.gotham.newsmediabrowser.common.imagebind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gotham.newsmediabrowser.common.config.ImageBindProperties;
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.media.MediaType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the real HTTP client, driven against an in-process {@link HttpServer} so the JSON
 * request/response and multipart wiring are exercised without the ImageBind model.
 */
class HttpImageBindClientTest {

    private HttpServer server;
    private HttpImageBindClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ImageBindProperties properties = new ImageBindProperties(baseUrl, false, Duration.ofSeconds(5));
        client = new HttpImageBindClient(HttpClient.newHttpClient(), properties);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void embedTextSendsJsonAndParses1024Vector() {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedPath = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();
        serve("/embed/text", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, embeddingJson(ImageBindClient.EMBEDDING_DIM));
        });

        float[] vector = client.embedText("gotham transit vote");

        assertThat(vector).hasSize(ImageBindClient.EMBEDDING_DIM);
        assertThat(vector[0]).isEqualTo(0.0f);
        assertThat(receivedPath.get()).isEqualTo("/embed/text");
        assertThat(receivedContentType.get()).isEqualTo("application/json");
        assertThat(receivedBody.get()).contains("\"text\"").contains("gotham transit vote");
    }

    @Test
    void embedMediaSendsMultipartToModalityPath() {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        serve("/embed/image", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, embeddingJson(ImageBindClient.EMBEDDING_DIM));
        });

        float[] vector = client.embedMedia(MediaType.IMAGE, new byte[] {1, 2, 3}, "pic.png", "image/png");

        assertThat(vector).hasSize(ImageBindClient.EMBEDDING_DIM);
        assertThat(contentType.get()).startsWith("multipart/form-data; boundary=");
        assertThat(body.get()).contains("name=\"file\"").contains("filename=\"pic.png\"").contains("image/png");
    }

    @Test
    void non2xxBecomesDependencyException() {
        serve("/embed/text", exchange -> respond(exchange, 503, "{\"detail\":\"loading\"}"));

        assertThatThrownBy(() -> client.embedText("x"))
                .isInstanceOf(DependencyException.class);
    }

    @Test
    void wrongDimensionBecomesDependencyException() {
        serve("/embed/text", exchange -> respond(exchange, 200, embeddingJson(3)));

        assertThatThrownBy(() -> client.embedText("x"))
                .isInstanceOf(DependencyException.class);
    }

    @Test
    void malformedBodyBecomesDependencyException() {
        serve("/embed/text", exchange -> respond(exchange, 200, "not json"));

        assertThatThrownBy(() -> client.embedText("x"))
                .isInstanceOf(DependencyException.class);
    }

    @Test
    void blankTextIsRejectedBeforeAnyCall() {
        assertThatThrownBy(() -> client.embedText(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---

    private void serve(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** Builds {@code {"modality":"text","dim":n,"embedding":[0,1,2,…]}} with {@code count} values. */
    private String embeddingJson(int count) {
        StringBuilder sb = new StringBuilder("{\"dim\":").append(count).append(",\"embedding\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append((float) i);
        }
        return sb.append("]}").toString();
    }
}
