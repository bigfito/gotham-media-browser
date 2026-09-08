package com.gotham.newsmediabrowser.datagen.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP client for Kokoro-82M Text-to-Speech audio generation.
 */
public class KokoroClient {

    private static final String SERVICE_NAME = "Kokoro";
    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public KokoroClient(String baseUrl, Duration timeout, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(60);
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public KokoroClient(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(60), null, null);
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/docs"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public GeneratedMedia synthesizeSpeech(String text, String voice, String title, String caption) {
        Objects.requireNonNull(text, "text must not be null");
        if (text.isBlank()) {
            throw new DatagenClientException(SERVICE_NAME, "Cannot synthesize audio for blank text");
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", "kokoro");
            payload.put("input", text);
            payload.put("voice", voice != null && !voice.isBlank() ? voice : "af_heart");
            payload.put("response_format", "wav");

            byte[] requestBody = objectMapper.writeValueAsBytes(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/audio/speech"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "audio/wav, audio/*, */*")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                String errorBody = response.body() != null ? new String(response.body()) : "empty";
                throw new DatagenClientException(SERVICE_NAME, response.statusCode(), "TTS API returned: " + errorBody);
            }

            byte[] audioBytes = response.body();
            if (audioBytes == null || audioBytes.length == 0) {
                throw new DatagenClientException(SERVICE_NAME, "Received empty audio response body");
            }

            String filename = "audio_" + UUID.randomUUID().toString().substring(0, 8) + ".wav";
            String resolvedTitle = title != null && !title.isBlank() ? title : "Audio: " + text.substring(0, Math.min(40, text.length()));
            String resolvedCaption = caption != null && !caption.isBlank() ? caption : text;

            return new GeneratedMedia(
                    audioBytes,
                    "audio/wav",
                    filename,
                    resolvedTitle,
                    resolvedCaption,
                    resolvedCaption,
                    resolvedCaption,
                    "Gotham Voice Dispatch"
            );
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DatagenClientException(SERVICE_NAME, "Failed to call Kokoro TTS API at " + baseUrl, e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private static String normalizeBaseUrl(String url) {
        Objects.requireNonNull(url, "baseUrl must not be null");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
