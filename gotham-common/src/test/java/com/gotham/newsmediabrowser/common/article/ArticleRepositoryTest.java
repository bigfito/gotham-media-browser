package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.gotham.newsmediabrowser.common.journalist.Journalist;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for the article document mapping (no Elasticsearch needed). The live CRUD + nested
 * query round-trip is covered by {@code ArticleRepositoryIT}.
 */
class ArticleRepositoryTest {

    private final ArticleRepository repository = new ArticleRepository(
            Mockito.mock(co.elastic.clients.elasticsearch.ElasticsearchClient.class));

    private final Journalist lois =
            new Journalist("j_lois", "Lois", "Lane", "lois@gotham.news", "Ace reporter", null, null);
    private final Journalist clark =
            new Journalist("j_clark", "Clark", "Kent", "clark@gotham.news", "Mild-mannered", null, null);

    private Article sampleArticle() {
        ArticleMetadata metadata = new ArticleMetadata("City", List.of("crime", "gotham"), "Gotham",
                "Wire", "SEO title", "SEO desc", "seo,keywords", "https://gotham.news/a/bat");
        List<ArticleJournalist> bylines = List.of(
                ArticleJournalist.fromJournalist(clark, 1, ContributionRole.CO_AUTHOR),
                ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR));
        return new Article("art1", "Bat-signal returns", "Subtitle", "Summary", "Body", "bat-signal",
                ArticleStatus.PUBLISHED, "en",
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-02T00:00:00Z"),
                Instant.parse("2026-09-03T00:00:00Z"),
                metadata, bylines);
    }

    @Test
    void toDocumentFlattensMetadataAndWritesProjectionsAndOrderedBylines() {
        Map<String, Object> document = repository.toDocument(sampleArticle());

        assertThat(document).containsEntry("title", "Bat-signal returns")
                .containsEntry("status", "PUBLISHED")
                .containsEntry("section", "City")
                .containsEntry("canonical_url", "https://gotham.news/a/bat")
                .containsEntry("published_at", "2026-09-01T00:00:00Z");
        assertThat(document).extracting("tags").isEqualTo(List.of("crime", "gotham"));

        // Projections rebuilt in byline order (Lois #0 before Clark #1).
        assertThat(document).extracting("journalist_names").isEqualTo(List.of("Lois Lane", "Clark Kent"));
        assertThat(document).extracting("journalist_bios").isEqualTo(List.of("Ace reporter", "Mild-mannered"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nested = (List<Map<String, Object>>) document.get("journalists");
        assertThat(nested).hasSize(2);
        assertThat(nested.get(0)).containsEntry("journalist_id", "j_lois")
                .containsEntry("full_name", "Lois Lane")
                .containsEntry("byline_order", 0)
                .containsEntry("contribution_role", "AUTHOR");
        assertThat(nested.get(1)).containsEntry("journalist_id", "j_clark")
                .containsEntry("byline_order", 1)
                .containsEntry("contribution_role", "CO_AUTHOR");
    }

    @Test
    void documentRoundTripsBackToAnEquivalentArticle() {
        Article original = sampleArticle();

        Article roundTripped = repository.fromSource("art1", repository.toDocument(original));

        assertThat(roundTripped.id()).isEqualTo("art1");
        assertThat(roundTripped.title()).isEqualTo("Bat-signal returns");
        assertThat(roundTripped.status()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(roundTripped.publishedAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(roundTripped.metadata()).isEqualTo(original.metadata());
        // Bylines come back ordered by byline_order, with roles preserved.
        assertThat(roundTripped.journalists()).extracting(ArticleJournalist::journalistId)
                .containsExactly("j_lois", "j_clark");
        assertThat(roundTripped.journalists().get(0).contributionRole()).isEqualTo(ContributionRole.AUTHOR);
    }

    @Test
    void nullContributionRoleAndEmptyBylinesAreHandled() {
        Article article = Article.newArticle("No byline", null, null, null, "no-byline",
                ArticleStatus.DRAFT, "en", null, ArticleMetadata.empty(), List.of());

        Map<String, Object> document = repository.toDocument(article);

        assertThat(document).extracting("journalist_names").isEqualTo(List.of());
        assertThat(document).extracting("journalists").isEqualTo(List.of());
        assertThat(repository.fromSource("x", document).journalists()).isEmpty();
    }
}
