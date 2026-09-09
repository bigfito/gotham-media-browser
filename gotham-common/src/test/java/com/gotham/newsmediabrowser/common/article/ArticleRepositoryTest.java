package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import com.gotham.newsmediabrowser.common.journalist.Journalist;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Unit tests for the article document mapping (no Elasticsearch needed). The live CRUD + nested
 * query round-trip is covered by {@code ArticleRepositoryIT}.
 */
class ArticleRepositoryTest {

    private final ArticleRepository repository = new ArticleRepository(
            Mockito.mock(ElasticsearchClient.class),
            new StubImageBindClient());

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
                800, 600, null, null, null, null, null, null, List.of(0.1f, 0.2f, 0.3f));
        ArticleMultimedia audio = new ArticleMultimedia("m1", com.gotham.newsmediabrowser.common.media.MediaType.AUDIO,
                "https://storage.googleapis.com/b/media/audio/y.mp3", "audio/mpeg", 0,
                null, null, "Clip", "Desc", null, "y.mp3", 5678L, null,
                null, null, 30000L, "mp3", 128, null, 44100, 2, null);
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

        // The asset_vector is written for the element that has one and omitted for the one that doesn't.
        assertThat(nested.get(1)).containsKey("asset_vector");
        assertThat(nested.get(0)).doesNotContainKey("asset_vector");

        Article roundTripped = repository.fromSource("art1", document);
        assertThat(roundTripped.multimedia()).extracting(ArticleMultimedia::multimediaElementId)
                .containsExactly("m1", "m2");
        assertThat(roundTripped.multimedia().get(1).width()).isEqualTo(800);
        assertThat(roundTripped.multimedia().get(0).mediaType())
                .isEqualTo(com.gotham.newsmediabrowser.common.media.MediaType.AUDIO);
        // asset_vector round-trips so it survives an edit save.
        assertThat(roundTripped.multimedia().get(1).assetVector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(roundTripped.multimedia().get(0).assetVector()).isNull();
    }

    @Test
    void toDocumentComputesA1024DimArticleEmbedding() {
        @SuppressWarnings("unchecked")
        List<Float> embedding = (List<Float>) repository.toDocument(sampleArticle()).get("article_embedding");

        assertThat(embedding).hasSize(1024);
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

    @Test
    void journalistSweepSkipsVectorsBecauseTheCascadeNeverRewritesThem() throws Exception {
        // The cascade writes through updateJournalistBylines (a partial update that leaves both
        // vector fields alone), so the sweep does not have to drag dense vectors over the wire.
        ElasticsearchClient client = Mockito.mock(ElasticsearchClient.class);
        ArticleRepository scanning = new ArticleRepository(client, new StubImageBindClient());
        when(client.search(any(SearchRequest.class), eq(Map.class))).thenReturn(emptyHits());

        scanning.findByJournalistId("j_lois");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), eq(Map.class));
        assertThat(captor.getValue().source().filter().excludes())
                .contains("article_embedding", "multimedia.asset_vector");
    }

    @Test
    void bylinePatchWritesOnlyBylineFields() {
        ArticleJournalist byline = new ArticleJournalist("j_lois", "Lois", "Lane", "lois@x", "bio",
                0, ContributionRole.AUTHOR);

        Map<String, Object> patch = repository.bylinePatch(List.of(byline));

        // Nothing else may appear here: any extra key would be rewritten on every cascade, and
        // article_embedding / multimedia are exactly what must survive one untouched.
        assertThat(patch).containsOnlyKeys(
                "journalists", "journalist_names", "journalist_bios", "updated_at");
        assertThat(patch).extracting("journalist_names").isEqualTo(List.of("Lois Lane"));
    }

    @Test
    void listExcludesVectorFields() throws Exception {
        ElasticsearchClient client = Mockito.mock(ElasticsearchClient.class);
        ArticleRepository listing = new ArticleRepository(client, new StubImageBindClient());
        when(client.search(any(SearchRequest.class), eq(Map.class))).thenReturn(emptyHits());

        listing.findAll(null, null, 0, 25);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), eq(Map.class));
        assertThat(captor.getValue().source().filter().excludes())
                .contains("article_embedding", "multimedia.asset_vector");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SearchResponse<Map> emptyHits() {
        return SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(ShardStatistics.of(s -> s.total(1).successful(1).failed(0)))
                .hits(h -> h.total(t -> t.value(0).relation(TotalHitsRelation.Eq)).hits(List.of())));
    }
}
