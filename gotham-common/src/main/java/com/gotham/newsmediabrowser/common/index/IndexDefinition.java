package com.gotham.newsmediabrowser.common.index;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The Elasticsearch indexes this application owns, each paired with the classpath location of its
 * approved mapping JSON.
 *
 * <p>The mapping files live once at the repository root ({@code elasticsearch/*.mapping.json}) and
 * are placed on the classpath by {@code gotham-common}'s build (see its {@code pom.xml}). This enum
 * is the single place that maps an index name to its mapping resource, so the idempotent bootstrap
 * (P2-T02) never hard-codes those paths.
 */
public enum IndexDefinition {

    /** Journalist master data; feeds the nested journalists on article documents. */
    JOURNALISTS("gotham-journalists"),

    /** Article documents with nested journalists and multimedia; the public search index. */
    MEDIA_BROWSER("gotham-media-browser");

    private static final String MAPPINGS_CLASSPATH_DIR = "elasticsearch/";

    private final String indexName;

    IndexDefinition(String indexName) {
        this.indexName = indexName;
    }

    /** The Elasticsearch index name (e.g. {@code gotham-journalists}). */
    public String indexName() {
        return indexName;
    }

    /** Classpath location of this index's mapping JSON (e.g. {@code elasticsearch/gotham-journalists.mapping.json}). */
    public String mappingResourcePath() {
        return MAPPINGS_CLASSPATH_DIR + indexName + ".mapping.json";
    }

    /**
     * Reads this index's mapping JSON from the classpath as a UTF-8 string.
     *
     * @return the raw mapping JSON (settings + mappings), ready to hand to the ES client
     * @throws IllegalStateException if the resource is missing or unreadable — that is a packaging
     *     error (the mapping was not shipped onto the classpath), so we fail fast with a clear message
     */
    public String loadMappingJson() {
        String resourcePath = mappingResourcePath();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Mapping resource not found on the classpath: " + resourcePath
                                + " — verify gotham-common packages elasticsearch/*.mapping.json.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read mapping resource: " + resourcePath, e);
        }
    }
}
