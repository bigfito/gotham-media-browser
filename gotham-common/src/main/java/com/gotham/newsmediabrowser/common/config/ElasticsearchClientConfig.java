package com.gotham.newsmediabrowser.common.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Elasticsearch Java API Client (9.4.x) from {@link ElasticsearchProperties}.
 *
 * <p>Uses the modern {@code ElasticsearchClient.of(...)} builder (rest5 transport / Apache
 * HttpClient 5). The client is created even with placeholder configuration so the app still
 * starts; calls simply fail until real values are supplied, and the health indicator (P1-T03)
 * reports the dependency as unavailable.
 *
 * <p>The API key is never logged — only the (non-secret) endpoint is.
 */
@Configuration(proxyBeanMethods = false)
public class ElasticsearchClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchClientConfig.class);

    /**
     * The Elasticsearch client bean. Spring closes it on shutdown ({@code close()} is inferred as
     * the destroy method).
     */
    @Bean
    ElasticsearchClient elasticsearchClient(ElasticsearchProperties properties) {
        if (properties.isConfigured()) {
            log.info("Configuring Elasticsearch client for endpoint {}", properties.endpoint());
        } else {
            log.warn("Elasticsearch is not fully configured (placeholder endpoint/API key). "
                    + "The client is created but requests will fail until real values are provided; "
                    + "health will report Unavailable.");
        }
        return ElasticsearchClient.of(builder -> builder
                .host(properties.endpoint())
                .apiKey(properties.apiKey()));
    }
}
