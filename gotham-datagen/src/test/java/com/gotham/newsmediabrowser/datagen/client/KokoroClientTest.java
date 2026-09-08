package com.gotham.newsmediabrowser.datagen.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KokoroClientTest {

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
    void isHealthy_whenDocsReturns200_returnsTrue() {
        server.createContext("/docs", exchange -> {
            byte[] response = "FastAPI Swagger Docs".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        KokoroClient client = new KokoroClient(baseUrl);
        assertThat(client.isHealthy()).isTrue();
    }

    @Test
    void isHealthy_whenDown_returnsFalse() {
        KokoroClient client = new KokoroClient("http://localhost:1");
        assertThat(client.isHealthy()).isFalse();
    }

    @Test
    void synthesizeSpeech_success_returnsWavMedia() {
        byte[] mockWavBytes = "RIFF1234WAVEfmt ".getBytes(StandardCharsets.UTF_8);

        server.createContext("/v1/audio/speech", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "audio/wav");
            exchange.sendResponseHeaders(200, mockWavBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(mockWavBytes);
            }
        });

        KokoroClient client = new KokoroClient(baseUrl);
        GeneratedMedia media = client.synthesizeSpeech(
                "Breaking news from Gotham City Hall.",
                "af_heart",
                "Breaking News Dispatch",
                "Anchor summary audio"
        );

        assertThat(media).isNotNull();
        assertThat(media.bytes()).isEqualTo(mockWavBytes);
        assertThat(media.mimeType()).isEqualTo("audio/wav");
        assertThat(media.filename()).startsWith("audio_").endsWith(".wav");
        assertThat(media.title()).isEqualTo("Breaking News Dispatch");
        assertThat(media.caption()).isEqualTo("Anchor summary audio");
    }

    @Test
    void synthesizeSpeech_blankText_throwsDatagenClientException() {
        KokoroClient client = new KokoroClient(baseUrl);
        assertThatThrownBy(() -> client.synthesizeSpeech("   ", "af_heart", "title", "caption"))
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("blank text");
    }

    @Test
    void synthesizeSpeech_non200_throwsDatagenClientException() {
        server.createContext("/v1/audio/speech", exchange -> {
            byte[] response = "TTS Engine error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        KokoroClient client = new KokoroClient(baseUrl);
        assertThatThrownBy(() -> client.synthesizeSpeech("Some news text", "af_heart", "title", "caption"))
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("Kokoro")
                .hasMessageContaining("500");
    }
}
