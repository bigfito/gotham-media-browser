package com.gotham.newsmediabrowser.web.health;

import com.gotham.newsmediabrowser.common.config.ImageBindProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Health check for the ImageBind embedding service via {@code GET {base-url}/health}.
 * Up means HTTP 2xx <strong>and</strong> {@code model_loaded} is not {@code false} (the process can
 * answer 200 while the real model is still warming).
 */
@Component
public class ImageBindHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(ImageBindHealthChecker.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
    /** Matches only the JSON member, so prose in another field cannot trip the check. */
    private static final Pattern MODEL_NOT_LOADED =
            Pattern.compile("\"model_loaded\"\\s*:\\s*false", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final URI healthUri;

    public ImageBindHealthChecker(HttpClient httpClient, ImageBindProperties properties) {
        this.httpClient = httpClient;
        this.healthUri = URI.create(properties.baseUrl().replaceAll("/+$", "") + "/health");
    }

    /** @return {@code true} when {@code /health} is 2xx and the model is loaded (or the field is absent). */
    public boolean isUp() {
        HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            return modelLoaded(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("ImageBind health check interrupted: {}", e.toString());
            return false;
        } catch (IOException e) {
            log.debug("ImageBind health check failed: {}", e.toString());
            return false;
        }
    }

    /**
     * True unless the body explicitly reports {@code "model_loaded": false}.
     *
     * <p>Deliberately a narrow regex rather than a JSON parse: gotham-web carries no Jackson (Spring
     * Boot 4 makes JSON opt-in) and pulling databind in for one health flag would also switch on JSON
     * message conversion for the whole app. Anchoring to the quoted key means that — unlike a
     * substring scan over a lower-cased body — text inside a {@code status} or {@code detail} field
     * cannot report a healthy service as down. An absent field means loaded: an older service build
     * simply does not publish it.
     */
    static boolean modelLoaded(String body) {
        return body == null || body.isBlank() || !MODEL_NOT_LOADED.matcher(body).find();
    }
}
