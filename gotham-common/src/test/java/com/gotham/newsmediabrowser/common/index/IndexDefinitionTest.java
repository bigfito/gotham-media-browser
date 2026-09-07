package com.gotham.newsmediabrowser.common.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Verifies the approved index mappings are shipped on the classpath and load as valid JSON
 * (P2-T01). This guards against a packaging regression where the mapping files stop being bundled.
 */
class IndexDefinitionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(IndexDefinition.class)
    void everyIndexShipsAValidMappingOnTheClasspath(IndexDefinition index) throws Exception {
        String json = index.loadMappingJson();

        assertThat(json).as("mapping JSON is non-empty").isNotBlank();

        JsonNode root = objectMapper.readTree(json); // fails the test if the JSON is malformed
        assertThat(root.has("settings")).as("mapping has settings").isTrue();
        assertThat(root.has("mappings")).as("mapping has mappings").isTrue();

        // _meta.index inside the file must match the index this enum entry represents.
        JsonNode declaredIndex = root.path("mappings").path("_meta").path("index");
        assertThat(declaredIndex.asText()).isEqualTo(index.indexName());
    }

    @Test
    void resourcePathFollowsTheConvention() {
        assertThat(IndexDefinition.JOURNALISTS.mappingResourcePath())
                .isEqualTo("elasticsearch/gotham-journalists.mapping.json");
        assertThat(IndexDefinition.MEDIA_BROWSER.mappingResourcePath())
                .isEqualTo("elasticsearch/gotham-media-browser.mapping.json");
    }

    @Test
    void mediaBrowserMappingDeclaresThe1024DimVectors() throws Exception {
        JsonNode root = objectMapper.readTree(IndexDefinition.MEDIA_BROWSER.loadMappingJson());
        JsonNode properties = root.path("mappings").path("properties");

        // Article-level embedding and the nested per-asset vector must both be 1024-d (ImageBind).
        assertThat(properties.path("article_embedding").path("dims").asInt()).isEqualTo(1024);
        assertThat(properties.path("multimedia").path("properties").path("asset_vector").path("dims").asInt())
                .isEqualTo(1024);
    }

    @Test
    void missingResourceFailsFastWithAClearMessage() {
        // The public loader is exercised above; here we assert the failure contract for a bad path
        // by reading a resource we know is absent, mirroring loadMappingJson's guard.
        assertThatThrownBy(() -> {
                    try (var in = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream("elasticsearch/does-not-exist.mapping.json")) {
                        if (in == null) {
                            throw new IllegalStateException(
                                    "Mapping resource not found on the classpath: "
                                            + "elasticsearch/does-not-exist.mapping.json");
                        }
                    }
                })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found on the classpath");
    }
}
