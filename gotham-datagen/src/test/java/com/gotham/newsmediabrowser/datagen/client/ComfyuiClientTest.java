package com.gotham.newsmediabrowser.datagen.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComfyuiClientTest {

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
    void isHealthy_whenSystemStatsReturns200_returnsTrue() {
        server.createContext("/system_stats", exchange -> {
            byte[] response = "{\"system\":{\"os\":\"posix\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        ComfyuiClient client = new ComfyuiClient(baseUrl);
        assertThat(client.isHealthy()).isTrue();
    }

    @Test
    void isHealthy_whenDown_returnsFalse() {
        ComfyuiClient client = new ComfyuiClient("http://localhost:1");
        assertThat(client.isHealthy()).isFalse();
    }

    @Test
    void generateImage_success_pollsHistoryAndDownloadsView() {
        byte[] mockPngBytes = "PNG_MOCK_IMAGE_DATA".getBytes(StandardCharsets.UTF_8);

        server.createContext("/prompt", exchange -> {
            String resp = "{\"prompt_id\":\"test-prompt-123\",\"number\":1}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/history/test-prompt-123", exchange -> {
            String historyJson = """
                    {
                      "test-prompt-123": {
                        "outputs": {
                          "9": {
                            "images": [
                              {
                                "filename": "Gotham_SDXL_0001.png",
                                "subfolder": "",
                                "type": "output"
                              }
                            ]
                          }
                        },
                        "status": {
                          "completed": true
                        }
                      }
                    }
                    """;
            byte[] bytes = historyJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/view", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, mockPngBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(mockPngBytes);
            }
        });

        ComfyuiClient client = new ComfyuiClient(baseUrl, Duration.ofSeconds(5), Duration.ofMillis(50), null, null);
        GeneratedMedia media = client.generateImage("Gotham Clocktower", "blur", "Clocktower Photo", "Night view");

        assertThat(media).isNotNull();
        assertThat(media.bytes()).isEqualTo(mockPngBytes);
        assertThat(media.mimeType()).isEqualTo("image/png");
        assertThat(media.filename()).isEqualTo("Gotham_SDXL_0001.png");
        assertThat(media.title()).isEqualTo("Clocktower Photo");
        assertThat(media.caption()).isEqualTo("Night view");
    }

    @Test
    void generateVideo_success_pollsHistoryAndDownloadsView() {
        byte[] mockMp4Bytes = "MP4_MOCK_VIDEO_DATA".getBytes(StandardCharsets.UTF_8);

        server.createContext("/prompt", exchange -> {
            String resp = "{\"prompt_id\":\"video-prompt-456\",\"number\":2}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/history/video-prompt-456", exchange -> {
            String historyJson = """
                    {
                      "video-prompt-456": {
                        "outputs": {
                          "9": {
                            "videos": [
                              {
                                "filename": "Gotham_Wan_0001.mp4",
                                "subfolder": "",
                                "type": "output"
                              }
                            ]
                          }
                        },
                        "status": {
                          "completed": true
                        }
                      }
                    }
                    """;
            byte[] bytes = historyJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/view", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "video/mp4");
            exchange.sendResponseHeaders(200, mockMp4Bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(mockMp4Bytes);
            }
        });

        ComfyuiClient client = new ComfyuiClient(baseUrl, Duration.ofSeconds(5), Duration.ofMillis(50), null, null);
        GeneratedMedia media = client.generateVideo("Gotham Traffic 5s", "blur", 5, "Traffic Clip", "Live traffic");

        assertThat(media).isNotNull();
        assertThat(media.bytes()).isEqualTo(mockMp4Bytes);
        assertThat(media.mimeType()).isEqualTo("video/mp4");
        assertThat(media.filename()).isEqualTo("Gotham_Wan_0001.mp4");
        assertThat(media.title()).isEqualTo("Traffic Clip");
    }

    @Test
    void generateImage_queueFails_throwsDatagenClientException() {
        server.createContext("/prompt", exchange -> {
            byte[] bytes = "Internal error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        ComfyuiClient client = new ComfyuiClient(baseUrl, Duration.ofSeconds(2), Duration.ofMillis(50), null, null);
        assertThatThrownBy(() -> client.generateImage("Prompt", "Negative", "Title", "Caption"))
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("ComfyUI")
                .hasMessageContaining("500");
    }

    @Test
    void generateImage_timeoutPolling_throwsDatagenClientException() {
        server.createContext("/prompt", exchange -> {
            String resp = "{\"prompt_id\":\"slow-prompt\",\"number\":3}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/history/slow-prompt", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        ComfyuiClient client = new ComfyuiClient(baseUrl, Duration.ofMillis(300), Duration.ofMillis(50), null, null);
        assertThatThrownBy(() -> client.generateImage("Prompt", "Negative", "Title", "Caption"))
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("Timed out waiting");
    }

    @Test
    void buildSdxlTurboGraph_validStructure() {
        ComfyuiClient client = new ComfyuiClient(baseUrl);
        ObjectNode graph = client.buildSdxlTurboGraph("Gotham Harbour", "low quality");

        assertThat(graph.has("3")).isTrue();
        assertThat(graph.has("4")).isTrue();
        assertThat(graph.has("9")).isTrue();
        assertThat(graph.path("6").path("inputs").path("text").asText()).isEqualTo("Gotham Harbour");
    }

    @Test
    void buildWanVideoGraph_validStructure() {
        ComfyuiClient client = new ComfyuiClient(baseUrl);
        ObjectNode graph = client.buildWanVideoGraph("Harbour boat cruise", "blur", 5);

        assertThat(graph.has("1")).isTrue();
        assertThat(graph.has("4")).isTrue();
        assertThat(graph.has("9")).isTrue();
        assertThat(graph.path("4").path("inputs").path("length").asInt()).isEqualTo(81);
        assertThat(graph.path("5").path("inputs").path("text").asText()).isEqualTo("Harbour boat cruise");
    }
}
