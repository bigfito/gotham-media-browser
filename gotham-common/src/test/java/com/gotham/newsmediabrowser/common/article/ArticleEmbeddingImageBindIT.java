package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.gotham.newsmediabrowser.common.config.ImageBindProperties;
import com.gotham.newsmediabrowser.common.imagebind.HttpImageBindClient;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.index.IndexDefinition;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Write-path embedding check across BOTH dependencies (P6-T03 / P9-T03): saving an article with the
 * real {@code imagebind-service} wired populates {@code article_embedding} with a 1024-d vector in
 * Elasticsearch. Skipped unless {@code ES_ENDPOINT} / {@code ES_API_KEY} and {@code IMAGEBIND_BASE_URL}
 * are all set (runs only under the {@code it-imagebind} profile).
 */
@Tag("integration")
@Tag("imagebind")
class ArticleEmbeddingImageBindIT {

    private static ElasticsearchClient client;
    private static String imagebindBaseUrl;

    @BeforeAll
    static void connect() {
        String endpoint = System.getenv("ES_ENDPOINT");
        String apiKey = System.getenv("ES_API_KEY");
        imagebindBaseUrl = System.getenv("IMAGEBIND_BASE_URL");
        assumeTrue(endpoint != null && !endpoint.isBlank(), "ES_ENDPOINT not set — skipping");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "ES_API_KEY not set — skipping");
        assumeTrue(imagebindBaseUrl != null && !imagebindBaseUrl.isBlank(), "IMAGEBIND_BASE_URL not set — skipping");

        ElasticsearchClient candidate = ElasticsearchClient.of(builder -> builder.host(endpoint).apiKey(apiKey));
        try {
            candidate.info();
        } catch (Exception e) {
            assumeTrue(false, "Elasticsearch not reachable — skipping: " + e.getMessage());
        }
        client = candidate;
    }

    @Test
    void articleSaveComputesArticleEmbedding() throws Exception {
        ImageBindClient imageBind = new HttpImageBindClient(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
                new ImageBindProperties(imagebindBaseUrl, false, Duration.ofSeconds(120)));
        ArticleRepository articles = new ArticleRepository(client, imageBind);
        String marker = "EMBED-" + System.nanoTime();

        Article created = null;
        try {
            created = articles.create(Article.newArticle(
                    "Transit funding vote " + marker, null, "City budget summary",
                    "The council debates transit funding across Gotham this week.",
                    "slug-" + marker, ArticleStatus.PUBLISHED, "en", null,
                    new ArticleMetadata("Politics", List.of(marker), null, null, null, null, null, null),
                    List.of()));

            // dense_vector is excluded from _source by default on Serverless — ask for it explicitly.
            final String id = created.id();
            GetResponse<Map> response = client.get(g -> g
                    .index(IndexDefinition.MEDIA_BROWSER.indexName())
                    .id(id)
                    .sourceIncludes("*"), Map.class);
            assertThat(response.found()).isTrue();
            Object embedding = response.source().get("article_embedding");
            assertThat(embedding).isInstanceOf(List.class);
            assertThat((List<?>) embedding).hasSize(ImageBindClient.EMBEDDING_DIM);
        } finally {
            if (created != null && created.id() != null) {
                articles.deleteById(created.id());
            }
        }
    }
}
