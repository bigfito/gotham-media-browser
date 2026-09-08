package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import com.gotham.newsmediabrowser.common.media.MediaType;
import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live nested multimedia FTS against a real {@code gotham-media-browser} index (P7-T03). Skipped
 * unless {@code ES_ENDPOINT} / {@code ES_API_KEY} are set and the cluster is reachable.
 */
@Tag("integration")
class MultimediaFullTextServiceIT {

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
    void nestedHitsSurfaceMatchedAssetWithStorageUri() {
        ArticleRepository repository = new ArticleRepository(client, new StubImageBindClient());
        MultimediaFullTextService service = new MultimediaFullTextService(client, repository);
        String marker = "MFTS-" + System.nanoTime();
        String storageUri = "https://storage.googleapis.com/aorozcoi-gotham-media-browser/media/image/"
                + marker + ".png";

        ArticleMultimedia match = new ArticleMultimedia(
                "m-hit-" + marker, MediaType.IMAGE, storageUri, "image/png", 0,
                "council chamber " + marker, null, "Chamber photo", "Inside the chamber", "alt chamber",
                marker + ".png", 12L, null, 1, 1, null, null, null, null, null, null, null);
        ArticleMultimedia miss = new ArticleMultimedia(
                "m-miss-" + marker, MediaType.AUDIO,
                "https://storage.googleapis.com/aorozcoi-gotham-media-browser/media/audio/" + marker + ".mp3",
                "audio/mpeg", 1, "stadium crowd", null, "Sports clip", "No chamber here", null,
                marker + ".mp3", 24L, null, null, null, 1000L, null, null, null, null, null, null);

        Article created = null;
        try {
            created = repository.create(Article.newArticle(
                    "Night at city hall " + marker, null, "Summary", "Body",
                    "slug-" + marker, ArticleStatus.PUBLISHED, "en",
                    Instant.parse("2026-03-01T00:00:00Z"),
                    new ArticleMetadata("Politics", List.of(marker), null, null, null, null, null, null),
                    List.of()).withMultimedia(List.of(match, miss)));

            MultimediaSearchPage page = service.search(new MultimediaFullTextQuery(
                    "council chamber " + marker,
                    List.of("multimedia.caption", "multimedia.title"),
                    List.of(ArticleStatus.PUBLISHED),
                    "Politics",
                    "en",
                    null,
                    null,
                    List.of(MediaType.IMAGE),
                    SearchSort.RELEVANCE,
                    1,
                    25));

            assertThat(page.total()).isGreaterThanOrEqualTo(1);
            assertThat(page.items()).extracting(hit -> hit.media().multimediaElementId())
                    .contains("m-hit-" + marker)
                    .doesNotContain("m-miss-" + marker);
            MultimediaSearchHit card = page.items().stream()
                    .filter(hit -> ("m-hit-" + marker).equals(hit.media().multimediaElementId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(card.articleId()).isEqualTo(created.id());
            assertThat(card.media().storageUri()).isEqualTo(storageUri);
            assertThat(card.media().mediaType()).isEqualTo(MediaType.IMAGE);
        } finally {
            if (created != null && created.id() != null) {
                repository.deleteById(created.id());
            }
        }
    }
}
