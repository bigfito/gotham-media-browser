package com.gotham.newsmediabrowser.datagen.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * HTTP client for Ollama LLM text generation (Qwen 2.5 7B Instruct).
 */
public class OllamaClient {

    private static final String SERVICE_NAME = "Ollama";
    private final String baseUrl;
    private final String modelName;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaClient(String baseUrl, String modelName, Duration timeout, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(120);
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public OllamaClient(String baseUrl, String modelName) {
        this(baseUrl, modelName, Duration.ofSeconds(120), null, null);
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public String generateText(String systemPrompt, String userPrompt) {
        return callChatApi(systemPrompt, userPrompt, false);
    }

    public String generateJson(String systemPrompt, String userPrompt) {
        return callChatApi(systemPrompt, userPrompt, true);
    }

    private String callChatApi(String systemPrompt, String userPrompt, boolean jsonFormat) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", modelName);
            payload.put("stream", false);

            if (jsonFormat) {
                payload.put("format", "json");
            }

            ArrayNode messages = payload.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode sysMsg = messages.addObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
            }

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt != null ? userPrompt : "");

            ObjectNode options = payload.putObject("options");
            options.put("temperature", 0.7);

            byte[] requestBody = objectMapper.writeValueAsBytes(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new DatagenClientException(SERVICE_NAME, response.statusCode(), "Chat API returned " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode messageNode = root.path("message").path("content");
            if (messageNode.isMissingNode() || messageNode.isNull()) {
                throw new DatagenClientException(SERVICE_NAME, "Missing message.content in response: " + response.body());
            }

            return messageNode.asText().trim();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DatagenClientException(SERVICE_NAME, "Failed to call Ollama chat API at " + baseUrl, e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    private static String normalizeBaseUrl(String url) {
        Objects.requireNonNull(url, "baseUrl must not be null");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
