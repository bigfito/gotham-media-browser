package com.gotham.newsmediabrowser.common.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import java.io.IOException;
import java.io.StringReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates the application's Elasticsearch indexes from their approved mappings, once, if they are
 * missing.
 *
 * <p>Idempotent by design: an index that already exists is left untouched, so running the bootstrap
 * on every boot is safe. It <strong>never deletes or modifies</strong> an existing index — schema
 * migrations, if ever needed, are a deliberate operator action, not a side effect of startup.
 */
@Component
public class IndexBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(IndexBootstrapper.class);

    /**
     * Index settings that Elasticsearch Serverless manages automatically and rejects if supplied. The
     * approved mapping files keep them (they are valid on a self-managed cluster); we strip them here
     * so the same mapping bootstraps cleanly against Serverless, the deployment target.
     */
    private static final Set<String> SERVERLESS_UNSUPPORTED_SETTINGS =
            Set.of("number_of_shards", "number_of_replicas");

    private final ElasticsearchClient client;

    public IndexBootstrapper(ElasticsearchClient client) {
        this.client = client;
    }

    /** The result of bootstrapping a single index. */
    public enum Outcome {
        /** The index did not exist and was created from its mapping. */
        CREATED,
        /** The index already existed and was left untouched. */
        ALREADY_EXISTS
    }

    /**
     * Ensures every {@link IndexDefinition} exists, creating the missing ones.
     *
     * @return what happened to each index (created vs. already existed)
     * @throws IndexBootstrapException if any index cannot be checked or created
     */
    public Map<IndexDefinition, Outcome> bootstrap() {
        Map<IndexDefinition, Outcome> outcomes = new EnumMap<>(IndexDefinition.class);
        for (IndexDefinition definition : IndexDefinition.values()) {
            outcomes.put(definition, createIfMissing(definition));
        }
        return outcomes;
    }

    private Outcome createIfMissing(IndexDefinition definition) {
        String indexName = definition.indexName();
        try {
            if (indexExists(indexName)) {
                log.info("Index '{}' already exists — leaving it untouched.", indexName);
                return Outcome.ALREADY_EXISTS;
            }
            create(definition);
            log.info("Index '{}' created from {}.", indexName, definition.mappingResourcePath());
            return Outcome.CREATED;
        } catch (IOException | ElasticsearchException e) {
            throw new IndexBootstrapException(
                    "Failed to bootstrap Elasticsearch index '" + indexName + "'", e);
        }
    }

    private boolean indexExists(String indexName) throws IOException {
        return client.indices().exists(request -> request.index(indexName)).value();
    }

    /**
     * Creates the index, feeding the mapping's {@code settings} and {@code mappings} sections to the
     * ES client separately (its create-index builder takes typed settings/mappings, not one raw JSON
     * blob). Each section is handed to {@code withJson(Reader)}, the client helper that parses a JSON
     * fragment into the typed builder — it brings its own JSON-P provider and mapper, so custom
     * pieces such as {@code _meta} and {@code dense_vector} deserialize exactly as the client expects
     * (the transport's own mapper does not expose a JSON-P provider, hence we do not reuse it here).
     */
    private void create(IndexDefinition definition) throws IOException {
        JsonObject mapping = readObject(definition.loadMappingJson());
        String settings = serverlessSafe(mapping.getJsonObject("settings")).toString();
        String mappings = mapping.getJsonObject("mappings").toString();

        client.indices().create(request -> request
                .index(definition.indexName())
                .settings(builder -> builder.withJson(new StringReader(settings)))
                .mappings(builder -> builder.withJson(new StringReader(mappings))));
    }

    private JsonObject readObject(String json) {
        try (JsonReader reader = JsonProvider.provider().createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    /**
     * Returns a copy of the settings without the entries Serverless rejects (see
     * {@link #SERVERLESS_UNSUPPORTED_SETTINGS}), whether they sit at the top level or under a nested
     * {@code index} object. Everything else (e.g. custom analyzers) is preserved verbatim.
     *
     * <p>Package-private so the stripping logic can be unit-tested directly (the create call's
     * builder lambda is not observable through a mocked client).
     */
    JsonObject serverlessSafe(JsonObject settings) {
        JsonObjectBuilder result = JsonProvider.provider().createObjectBuilder();
        for (Map.Entry<String, JsonValue> entry : settings.entrySet()) {
            String key = entry.getKey();
            JsonValue value = entry.getValue();
            if (SERVERLESS_UNSUPPORTED_SETTINGS.contains(key)) {
                log.debug("Omitting index setting '{}' — managed automatically by Elasticsearch Serverless.", key);
            } else if (key.equals("index") && value.getValueType() == JsonValue.ValueType.OBJECT) {
                result.add(key, serverlessSafe(value.asJsonObject()));
            } else {
                result.add(key, value);
            }
        }
        return result.build();
    }
}
