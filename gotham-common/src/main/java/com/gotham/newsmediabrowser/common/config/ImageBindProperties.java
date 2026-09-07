package com.gotham.newsmediabrowser.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ImageBind embedding-service settings.
 *
 * <p>Bound from {@code gotham.imagebind.*}. In Docker Compose the base URL points at the
 * {@code imagebind-service} container ({@code http://imagebind-service:8081}); for local IDE runs
 * it can be overridden to {@code http://127.0.0.1:8081}.
 *
 * @param baseUrl        base URL of the embedding service (no trailing slash)
 * @param stub           when {@code true}, the client returns deterministic offline vectors and makes
 *                       no HTTP calls — for CI and local runs without the model weights
 * @param requestTimeout per-request timeout for embedding calls (CPU video embeds are slow);
 *                       defaults to 60s when not set
 */
@ConfigurationProperties(prefix = "gotham.imagebind")
public record ImageBindProperties(String baseUrl, boolean stub, Duration requestTimeout) {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    public ImageBindProperties {
        if (requestTimeout == null) {
            requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        }
    }
}
