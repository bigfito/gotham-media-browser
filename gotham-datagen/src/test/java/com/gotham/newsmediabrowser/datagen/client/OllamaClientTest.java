package com.gotham.newsmediabrowser.datagen.client;

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

class OllamaClientTest {

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
    void isHealthy_whenTagsReturns200_returnsTrue() {
        server.createContext("/api/tags", exchange -> {
            byte[] response = "{\"models\":[{\"name\":\"qwen2.5:7b-instruct\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        OllamaClient client = new OllamaClient(baseUrl, "qwen2.5:7b-instruct");
        assertThat(client.isHealthy()).isTrue();
    }

    @Test
    void isHealthy_whenDownOrError_returnsFalse() {
        OllamaClient client = new OllamaClient("http://localhost:1", "qwen2.5:7b-instruct");
        assertThat(client.isHealthy()).isFalse();
    }

    @Test
    void generateText_success() {
        server.createContext("/api/chat", exchange -> {
            String json = """
                    {
                      "model": "qwen2.5:7b-instruct",
                      "message": {
                        "role": "assistant",
                        "content": "Mayor Cobblepot announced new waterfront transit funding today."
                      },
                      "done": true
                    }
                    """;
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        OllamaClient client = new OllamaClient(baseUrl, "qwen2.5:7b-instruct");
        String text = client.generateText("You are a reporter.", "Write a headline.");

        assertThat(text).isEqualTo("Mayor Cobblepot announced new waterfront transit funding today.");
    }

    @Test
    void generateJson_success() {
        server.createContext("/api/chat", exchange -> {
            String json = """
                    {
                      "model": "qwen2.5:7b-instruct",
                      "message": {
                        "role": "assistant",
                        "content": "{\\"firstName\\":\\"Vicki\\",\\"lastName\\":\\"Vale\\"}"
                      },
                      "done": true
                    }
                    """;
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        OllamaClient client = new OllamaClient(baseUrl, "qwen2.5:7b-instruct");
        String result = client.generateJson("System prompt", "Generate journalist JSON");

        assertThat(result).contains("\"firstName\":\"Vicki\"");
    }

    @Test
    void generateText_serverError_throwsDatagenClientException() {
        server.createContext("/api/chat", exchange -> {
            byte[] response = "Model not found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        OllamaClient client = new OllamaClient(baseUrl, "qwen2.5:7b-instruct");
        assertThatThrownBy(() -> client.generateText("sys", "user"))
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("Ollama")
                .hasMessageContaining("404");
    }

    @Test
    void generateText_missingContent_throwsDatagenClientException() {
        server.createContext("/api/chat", exchange -> {
            byte[] response = "{\"model\":\"qwen2.5:7b-instruct\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        OllamaClient client = new OllamaClient(baseUrl, "qwen2.5:7b-instruct");
        assertThatThrownBy(() -> client.generateText("sys", "user"))
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("Missing message.content");
    }
}
