package com.gotham.newsmediabrowser.common.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.gotham.newsmediabrowser.common.index.IndexBootstrapper.Outcome;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live index bootstrap against a real cluster (P2-T02 / P9-T03): both indexes exist after bootstrap,
 * and a second run is a no-op (idempotent, never deletes). Skipped unless {@code ES_ENDPOINT} /
 * {@code ES_API_KEY} are set.
 */
@Tag("integration")
class IndexBootstrapIT {

    private static ElasticsearchClient client;

    @BeforeAll
    static void connect() {
        String endpoint = System.getenv("ES_ENDPOINT");
        String apiKey = System.getenv("ES_API_KEY");
        assumeTrue(endpoint != null && !endpoint.isBlank(), "ES_ENDPOINT not set — skipping live ES test");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "ES_API_KEY not set — skipping live ES test");

        ElasticsearchClient candidate = ElasticsearchClient.of(builder -> builder.host(endpoint).apiKey(apiKey));
        try {
            candidate.info();
        } catch (Exception e) {
            assumeTrue(false, "Elasticsearch not reachable — skipping: " + e.getMessage());
        }
        client = candidate;
    }

    @Test
    void bootstrapIsIdempotentAndCreatesBothIndexes() {
        IndexBootstrapper bootstrapper = new IndexBootstrapper(client);

        Map<IndexDefinition, Outcome> first = bootstrapper.bootstrap();
        assertThat(first).containsOnlyKeys(IndexDefinition.JOURNALISTS, IndexDefinition.MEDIA_BROWSER);
        assertThat(first.values()).allMatch(o -> o == Outcome.CREATED || o == Outcome.ALREADY_EXISTS);

        // Second run must leave everything in place — no index is (re)created.
        Map<IndexDefinition, Outcome> second = bootstrapper.bootstrap();
        assertThat(second.values()).containsOnly(Outcome.ALREADY_EXISTS);
    }
}
