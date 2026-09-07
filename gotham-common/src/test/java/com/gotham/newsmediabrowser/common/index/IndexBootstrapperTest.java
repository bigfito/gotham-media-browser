package com.gotham.newsmediabrowser.common.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import java.io.IOException;
import java.util.function.Function;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the index bootstrap control flow: create when missing, skip when present
 * (idempotent), never delete, and fail with a clear, index-named exception. The real create call
 * against Elasticsearch is verified live (see the P2-T02 state note).
 */
@ExtendWith(MockitoExtension.class)
class IndexBootstrapperTest {

    @Mock
    private ElasticsearchClient client;

    @Mock
    private ElasticsearchIndicesClient indices;

    @Test
    void createsEveryIndexWhenNoneExist() throws IOException {
        when(client.indices()).thenReturn(indices);
        when(indices.exists(any(Function.class))).thenReturn(new BooleanResponse(false));
        when(indices.create(any(Function.class))).thenReturn(mock(CreateIndexResponse.class));

        Map<IndexDefinition, IndexBootstrapper.Outcome> outcomes = new IndexBootstrapper(client).bootstrap();

        assertThat(outcomes.values()).containsOnly(IndexBootstrapper.Outcome.CREATED);
        verify(indices, times(IndexDefinition.values().length)).create(any(Function.class));
        verify(indices, never()).delete(any(Function.class));
    }

    @Test
    void skipsExistingIndexesAndCreatesNothing() throws IOException {
        when(client.indices()).thenReturn(indices);
        when(indices.exists(any(Function.class))).thenReturn(new BooleanResponse(true));

        Map<IndexDefinition, IndexBootstrapper.Outcome> outcomes = new IndexBootstrapper(client).bootstrap();

        assertThat(outcomes.values()).containsOnly(IndexBootstrapper.Outcome.ALREADY_EXISTS);
        verify(indices, never()).create(any(Function.class));
        verify(indices, never()).delete(any(Function.class));
    }

    @Test
    void serverlessSafeStripsShardAndReplicaSettingsButKeepsTheRest() {
        IndexBootstrapper bootstrapper = new IndexBootstrapper(client);
        var provider = jakarta.json.spi.JsonProvider.provider();

        // Both top-level (as in our mappings) and nested-under-index forms must be stripped.
        jakarta.json.JsonObject settings = provider.createObjectBuilder()
                .add("number_of_shards", 1)
                .add("number_of_replicas", 0)
                .add("analysis", provider.createObjectBuilder().add("analyzer", "x"))
                .add("index", provider.createObjectBuilder()
                        .add("number_of_shards", 3)
                        .add("refresh_interval", "1s"))
                .build();

        jakarta.json.JsonObject safe = bootstrapper.serverlessSafe(settings);

        assertThat(safe.containsKey("number_of_shards")).isFalse();
        assertThat(safe.containsKey("number_of_replicas")).isFalse();
        assertThat(safe.containsKey("analysis")).as("custom analyzers preserved").isTrue();
        assertThat(safe.getJsonObject("index").containsKey("number_of_shards")).isFalse();
        assertThat(safe.getJsonObject("index").getString("refresh_interval")).isEqualTo("1s");
    }

    @Test
    void wrapsClientFailureInIndexBootstrapExceptionNamingTheIndex() throws IOException {
        when(client.indices()).thenReturn(indices);
        when(indices.exists(any(Function.class))).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> new IndexBootstrapper(client).bootstrap())
                .isInstanceOf(IndexBootstrapException.class)
                // First index in enum order; message must name it and hide the raw cause text.
                .hasMessageContaining(IndexDefinition.values()[0].indexName())
                .hasMessageNotContaining("connection refused");
    }
}
