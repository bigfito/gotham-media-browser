package com.gotham.newsmediabrowser.web.health;

import com.gotham.newsmediabrowser.common.config.ImageBindProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Health check for the ImageBind embedding service via {@code GET {base-url}/health}.
 * A 2xx response means "up"; timeouts/errors mean "down" (logged at DEBUG).
 */
@Component
public class ImageBindHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(ImageBindHealthChecker.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient;
    private final URI healthUri;

    public ImageBindHealthChecker(HttpClient httpClient, ImageBindProperties properties) {
        this.httpClient = httpClient;
        this.healthUri = URI.create(properties.baseUrl().replaceAll("/+$", "") + "/health");
    }

    /** @return {@code true} when {@code /health} returns 2xx, {@code false} otherwise. */
    public boolean isUp() {
        HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("ImageBind health check interrupted: {}", e.toString());
            return false;
        } catch (IOException e) {
            log.debug("ImageBind health check failed: {}", e.toString());
            return false;
        }
    }
}
