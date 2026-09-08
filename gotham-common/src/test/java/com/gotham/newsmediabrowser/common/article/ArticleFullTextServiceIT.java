package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import com.gotham.newsmediabrowser.common.journalist.Journalist;
import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live article FTS against a real {@code gotham-media-browser} index (P7-T02). Skipped unless
 * {@code ES_ENDPOINT} / {@code ES_API_KEY} are set and the cluster is reachable.
 */
@Tag("integration")
class ArticleFullTextServiceIT {

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
    void sampleQueryReturnsExpectedHitsAndHonorsPageSize() {
        ArticleRepository repository = new ArticleRepository(client, new StubImageBindClient());
        ArticleFullTextService service = new ArticleFullTextService(client, repository);
        String marker = "FTS-" + System.nanoTime();
        String journalistId = "j_" + marker;
        Journalist lois = new Journalist(journalistId, "Lois", marker, "lois@gotham.news", "Ace", null, null);
        List<ArticleJournalist> bylines =
                List.of(ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR));

        List<Article> created = new ArrayList<>();
        try {
            created.add(repository.create(Article.newArticle(
                    "Transit funding " + marker, null, "City budget summary",
                    "The council debates transit funding this week.",
                    "slug-hit-" + marker, ArticleStatus.PUBLISHED, "en",
                    Instant.parse("2026-03-01T00:00:00Z"),
                    new ArticleMetadata("Politics", List.of(marker), "Gotham", null, null, null, null, null),
                    bylines)));
            created.add(repository.create(Article.newArticle(
                    "Unrelated sports recap " + marker, null, "No transit here",
                    "A quiet night at the stadium.",
                    "slug-miss-" + marker, ArticleStatus.PUBLISHED, "en",
                    Instant.parse("2026-03-02T00:00:00Z"),
                    new ArticleMetadata("Sports", List.of(marker), "Gotham", null, null, null, null, null),
                    bylines)));

            ArticlePage hits = service.search(new ArticleFullTextQuery(
                    "transit funding",
                    List.of("title", "body"),
                    List.of(ArticleStatus.PUBLISHED),
                    "Politics",
                    "en",
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-12-31T00:00:00Z"),
                    journalistId,
                    SearchSort.RELEVANCE,
                    1,
                    25));

            assertThat(hits.total()).isGreaterThanOrEqualTo(1);
            assertThat(hits.items()).extracting(Article::id).contains(created.get(0).id());
            assertThat(hits.items()).extracting(Article::id).doesNotContain(created.get(1).id());

            ArticlePage page50 = service.search(new ArticleFullTextQuery(
                    marker, List.of("title"), List.of(), null, null, null, null, null,
                    SearchSort.RELEVANCE, 1, 50));
            assertThat(page50.items().size()).isLessThanOrEqualTo(50);

            ArticlePage page100 = service.search(new ArticleFullTextQuery(
                    marker, List.of("title"), List.of(), null, null, null, null, null,
                    SearchSort.RELEVANCE, 1, 100));
            assertThat(page100.items().size()).isLessThanOrEqualTo(100);
        } finally {
            for (Article article : created) {
                if (article != null && article.id() != null) {
                    repository.deleteById(article.id());
                }
            }
        }
    }
}
