package com.gotham.newsmediabrowser.common.journalist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.gotham.newsmediabrowser.common.article.Article;
import com.gotham.newsmediabrowser.common.article.ArticleJournalist;
import com.gotham.newsmediabrowser.common.article.ArticleMetadata;
import com.gotham.newsmediabrowser.common.article.ArticleRepository;
import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.article.ContributionRole;
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live cascade-strip delete against a real cluster (P4-T03 / P9-T03): deleting a journalist strips
 * their nested byline from every article and reindexes, then removes the master. Skipped unless
 * {@code ES_ENDPOINT} / {@code ES_API_KEY} are set.
 */
@Tag("integration")
class JournalistServiceIT {

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
    void cascadeDeleteStripsBylineAndRemovesMaster() {
        JournalistRepository journalists = new JournalistRepository(client);
        ArticleRepository articles = new ArticleRepository(client, new StubImageBindClient());
        JournalistService service = new JournalistService(journalists, articles);
        String marker = "CASCADE-" + System.nanoTime();

        Journalist journalist = journalists.create(new Journalist(
                null, "Cascade", marker, "cascade-" + marker + "@example.test", "Bio", null, null));
        Article article = null;
        try {
            article = articles.create(Article.newArticle(
                    "Byline holder " + marker, null, "Summary", "Body",
                    "slug-" + marker, ArticleStatus.PUBLISHED, "en", Instant.parse("2026-03-01T00:00:00Z"),
                    new ArticleMetadata("Politics", List.of(marker), null, null, null, null, null, null),
                    List.of(ArticleJournalist.fromJournalist(journalist, 0, ContributionRole.AUTHOR))));
            assertThat(article.journalists()).hasSize(1);

            service.cascadeDelete(journalist.id());

            assertThat(journalists.findById(journalist.id())).isEmpty();
            Optional<Article> reindexed = articles.findById(article.id());
            assertThat(reindexed).isPresent();
            assertThat(reindexed.get().journalists()).isEmpty();
        } finally {
            if (article != null && article.id() != null) {
                articles.deleteById(article.id());
            }
        }
    }
}
