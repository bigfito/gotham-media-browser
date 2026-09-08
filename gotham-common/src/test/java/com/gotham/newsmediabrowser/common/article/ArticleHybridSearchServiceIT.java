package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live article hybrid (RRF) search against a real {@code gotham-media-browser} index (P8-T02). Uses
 * the deterministic {@link StubImageBindClient} and a unique {@code section} filter to isolate the
 * seeded document, proving the RRF DSL (BM25 + kNN legs, shared filters) is accepted by the live
 * Serverless cluster. Skipped unless {@code ES_ENDPOINT} / {@code ES_API_KEY} are set.
 */
@Tag("integration")
class ArticleHybridSearchServiceIT {

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
    void hybridSearchFusesLegsAndReturnsFilteredCandidate() {
        StubImageBindClient embedder = new StubImageBindClient();
        ArticleRepository repository = new ArticleRepository(client, embedder);
        ArticleFullTextService fullText = new ArticleFullTextService(client, repository);
        ArticleSemanticSearchService semantic = new ArticleSemanticSearchService(client, repository, embedder);
        ArticleHybridSearchService service =
                new ArticleHybridSearchService(client, repository, fullText, semantic);
        String marker = "HYB-" + System.nanoTime();
        String section = "HybSection-" + marker;

        Article created = null;
        try {
            created = repository.create(Article.newArticle(
                    "Transit funding vote " + marker, null, "City budget summary",
                    "The council debates transit funding this week.",
                    "slug-" + marker, ArticleStatus.PUBLISHED, "en",
                    Instant.parse("2026-03-01T00:00:00Z"),
                    new ArticleMetadata(section, List.of(marker), null, null, null, null, null, null),
                    List.of()));

            ArticlePage page = service.search(new ArticleFullTextQuery(
                    "transit funding", List.of(), List.of(ArticleStatus.PUBLISHED), section, "en",
                    null, null, null, null, 1, 25));

            assertThat(page.total()).isGreaterThanOrEqualTo(1);
            assertThat(page.items()).extracting(Article::id).contains(created.id());
        } finally {
            if (created != null && created.id() != null) {
                repository.deleteById(created.id());
            }
        }
    }
}
