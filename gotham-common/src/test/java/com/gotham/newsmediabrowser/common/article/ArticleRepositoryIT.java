package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.gotham.newsmediabrowser.common.journalist.Journalist;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live CRUD + nested-journalist-query round-trip against a real {@code gotham-media-browser} index
 * (P4-T02 verification). Skipped unless {@code ES_ENDPOINT} / {@code ES_API_KEY} are set and the
 * cluster is reachable. Wired into the {@code it-es} Failsafe profile in P9-T03; runnable now with:
 *
 * <pre>mvn -pl gotham-common test -Dtest=ArticleRepositoryIT -DfailIfNoTests=false</pre>
 */
@Tag("integration")
class ArticleRepositoryIT {

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
    void createReadUpdateFilterNestedQueryDelete() {
        ArticleRepository repository = new ArticleRepository(client);
        String marker = "IT-" + System.nanoTime();
        String journalistId = "j_" + marker;

        Journalist snapshotSource = new Journalist(journalistId, "Vicki", marker,
                "vicki@gotham.news", "Photojournalist", null, null);
        List<ArticleJournalist> bylines =
                List.of(ArticleJournalist.fromJournalist(snapshotSource, 0, ContributionRole.AUTHOR));

        Article created = null;
        try {
            // create
            created = repository.create(Article.newArticle("Headline " + marker, null, "Summary",
                    "Body text", "slug-" + marker, ArticleStatus.DRAFT, "en", null,
                    new ArticleMetadata("City", List.of(marker), null, null, null, null, null, null),
                    bylines));
            assertThat(created.id()).isNotBlank();
            assertThat(created.createdAt()).isNotNull();

            // read: bylines and metadata round-trip
            Optional<Article> fetched = repository.findById(created.id());
            assertThat(fetched).isPresent();
            assertThat(fetched.get().journalists()).extracting(ArticleJournalist::journalistId)
                    .containsExactly(journalistId);
            assertThat(fetched.get().status()).isEqualTo(ArticleStatus.DRAFT);

            // update: flip to PUBLISHED
            Article edited = repository.update(new Article(created.id(), created.title(), null, "Summary",
                    "Body text", created.slug(), ArticleStatus.PUBLISHED, "en", null,
                    created.createdAt(), created.updatedAt(), created.metadata(), bylines));
            assertThat(repository.findById(created.id())).get()
                    .extracting(Article::status).isEqualTo(ArticleStatus.PUBLISHED);

            // list filter by status finds it as PUBLISHED, not as DRAFT
            String createdId = created.id();
            assertThat(repository.findAll(ArticleStatus.PUBLISHED, journalistId, 0, 50).items())
                    .anyMatch(a -> createdId.equals(a.id()));
            assertThat(repository.findAll(ArticleStatus.DRAFT, journalistId, 0, 50).items())
                    .noneMatch(a -> createdId.equals(a.id()));

            // nested journalist query (the cascade-strip lookup)
            List<Article> byJournalist = repository.findByJournalistId(journalistId);
            assertThat(byJournalist).extracting(Article::id).containsExactly(createdId);
        } finally {
            if (created != null && created.id() != null) {
                assertThat(repository.deleteById(created.id())).isTrue();
                assertThat(repository.findById(created.id())).isEmpty();
            }
        }
    }
}
