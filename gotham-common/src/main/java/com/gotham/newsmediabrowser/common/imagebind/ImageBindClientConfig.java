package com.gotham.newsmediabrowser.common.imagebind;

import com.gotham.newsmediabrowser.common.config.ImageBindProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link ImageBindClient} bean, choosing the backend from
 * {@code gotham.imagebind.stub}: {@code true} → offline {@link StubImageBindClient} (CI / no
 * weights), otherwise the real {@link HttpImageBindClient}. The HTTP client here is dedicated to
 * embedding calls (bounded connect timeout); the per-request timeout comes from the properties.
 */
@Configuration
public class ImageBindClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ImageBindClientConfig.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public ImageBindClient imageBindClient(ImageBindProperties properties) {
        if (properties.stub()) {
            log.info("ImageBind client in STUB mode — deterministic offline vectors, no HTTP calls.");
            return new StubImageBindClient();
        }
        log.info("ImageBind client using HTTP backend at {}", properties.baseUrl());
        // Force HTTP/1.1: the imagebind-service runs on uvicorn (h11), and java.net.http's default
        // HTTP/2 attempt over cleartext drops the request body against an HTTP/1.1-only server.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        return new HttpImageBindClient(httpClient, properties);
    }
}
