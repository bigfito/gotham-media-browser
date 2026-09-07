package com.gotham.newsmediabrowser.web.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Health check for Elasticsearch, safe for Elastic Cloud Serverless.
 *
 * <p>Uses the lightweight {@code info()} call (GET /) — <strong>not</strong> {@code ping()} or
 * cluster-health, which are restricted on Serverless. Any failure is treated as "down" and logged
 * at DEBUG (never the API key).
 */
@Component
public class ElasticsearchHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchHealthChecker.class);

    private final ElasticsearchClient client;

    public ElasticsearchHealthChecker(ElasticsearchClient client) {
        this.client = client;
    }

    /** @return {@code true} when the cluster answers the info call, {@code false} otherwise. */
    public boolean isUp() {
        try {
            client.info();
            return true;
        } catch (Exception e) {
            log.debug("Elasticsearch health check failed: {}", e.toString());
            return false;
        }
    }
}
