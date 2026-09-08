package com.gotham.newsmediabrowser.datagen;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Shared env + skip helpers for the {@code it-datagen-helpers} Failsafe suite.
 *
 * <p>Defaults match Compose profile {@code datagen}. Each IT probes health and
 * {@code assumeTrue}-skips when the container (or {@code gotham-web}) is down, so
 * {@code mvn -Pit-datagen-helpers verify} stays green without Docker.
 */
public final class DatagenITSupport {

    public static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    public static final String DEFAULT_COMFYUI_URL = "http://localhost:8188";
    public static final String DEFAULT_KOKORO_URL = "http://localhost:8880";
    public static final String DEFAULT_WEB_URL = "http://localhost:8080";
    public static final String DEFAULT_TEXT_MODEL = "qwen2.5:7b-instruct";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private DatagenITSupport() {
    }

    public static String ollamaUrl() {
        return envOrDefault("OLLAMA_URL", DEFAULT_OLLAMA_URL);
    }

    public static String comfyuiUrl() {
        return envOrDefault("COMFYUI_URL", DEFAULT_COMFYUI_URL);
    }

    public static String kokoroUrl() {
        return envOrDefault("KOKORO_URL", DEFAULT_KOKORO_URL);
    }

    public static String webUrl() {
        return envOrDefault("GOTHAM_WEB_URL", DEFAULT_WEB_URL);
    }

    public static String textModel() {
        return envOrDefault("OLLAMA_MODEL", DEFAULT_TEXT_MODEL);
    }

    /**
     * Real Wan T2V on CPU is minutes-per-clip. ITs run video only against the in-repo
     * ComfyUI stub, or when the operator sets {@code DATAGEN_IT_VIDEO=true}.
     */
    public static boolean runComfyuiVideo() {
        return booleanEnv("DATAGEN_IT_VIDEO") || comfyuiLooksLikeStub(comfyuiUrl());
    }

    public static void assumeHealthy(boolean healthy, String service, String url) {
        assumeTrue(healthy, service + " not reachable at " + url + " — skipping (start compose profile datagen)");
    }

    public static boolean comfyuiLooksLikeStub(String baseUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(baseUrl) + "/system_stats"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() != null ? response.body().toLowerCase(Locale.ROOT) : "";
            return response.statusCode() == 200 && body.contains("stub");
        } catch (Exception e) {
            return false;
        }
    }

    public static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return value.strip();
        }
        return fallback;
    }

    public static boolean booleanEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
