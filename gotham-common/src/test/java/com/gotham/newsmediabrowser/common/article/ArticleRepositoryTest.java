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
                metadata, bylines, List.of());
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
    void multimediaIsNestedOrderedWithProjectionAndRoundTrips() {
        ArticleMultimedia image = new ArticleMultimedia("m2", com.gotham.newsmediabrowser.common.media.MediaType.IMAGE,
                "https://storage.googleapis.com/b/media/image/x.png", "image/png", 1,
                "A caption", "Credit", "Photo title", null, "Alt text", "x.png", 1234L, null,
                800, 600, null, null, null, null, null, null);
        ArticleMultimedia audio = new ArticleMultimedia("m1", com.gotham.newsmediabrowser.common.media.MediaType.AUDIO,
                "https://storage.googleapis.com/b/media/audio/y.mp3", "audio/mpeg", 0,
                null, null, "Clip", "Desc", null, "y.mp3", 5678L, null,
                null, null, 30000L, "mp3", 128, null, 44100, 2);
        Article article = new Article("art1", "T", null, null, null, "t", ArticleStatus.PUBLISHED, "en",
                null, null, null, ArticleMetadata.empty(), List.of(), List.of(image, audio));

        Map<String, Object> document = repository.toDocument(article);

        // multimedia_text projection follows position order (audio #0 title/desc, then image #1 fields).
        assertThat(document).extracting("multimedia_text")
                .isEqualTo(List.of("Clip", "Desc", "Photo title", "A caption", "Credit", "Alt text"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nested = (List<Map<String, Object>>) document.get("multimedia");
        assertThat(nested).hasSize(2);
        assertThat(nested.get(0)).containsEntry("multimedia_element_id", "m1")
                .containsEntry("media_type", "AUDIO")
                .containsEntry("position", 0)
                .containsEntry("duration_ms", 30000L);

        Article roundTripped = repository.fromSource("art1", document);
        assertThat(roundTripped.multimedia()).extracting(ArticleMultimedia::multimediaElementId)
                .containsExactly("m1", "m2");
        assertThat(roundTripped.multimedia().get(1).width()).isEqualTo(800);
        assertThat(roundTripped.multimedia().get(0).mediaType())
                .isEqualTo(com.gotham.newsmediabrowser.common.media.MediaType.AUDIO);
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
