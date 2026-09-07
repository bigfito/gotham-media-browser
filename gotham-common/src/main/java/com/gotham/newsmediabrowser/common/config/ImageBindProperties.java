package com.gotham.newsmediabrowser.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ImageBind embedding-service settings.
 *
 * <p>Bound from {@code gotham.imagebind.*}. In Docker Compose the base URL points at the
 * {@code imagebind-service} container ({@code http://imagebind-service:8081}); for local IDE runs
 * it can be overridden to {@code http://127.0.0.1:8081}.
 *
 * @param baseUrl base URL of the embedding service (no trailing slash)
 */
@ConfigurationProperties(prefix = "gotham.imagebind")
public record ImageBindProperties(String baseUrl) {
}
