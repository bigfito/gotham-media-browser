package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live multimedia semantic (nested kNN + {@code inner_hits}) search against a real
 * {@code gotham-media-browser} index (P8-T01). Seeds a nested asset carrying a deterministic
 * {@link StubImageBindClient} {@code asset_vector} and isolates it with a unique {@code section}
 * filter, proving the nested kNN DSL is accepted live and flattens back to a card. Skipped unless
 * {@code ES_ENDPOINT} / {@code ES_API_KEY} are set.
 */
@Tag("integration")
class MultimediaSemanticSearchServiceIT {

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
    void nestedSemanticSearchReturnsMatchedAssetCard() {
        StubImageBindClient embedder = new StubImageBindClient();
        ArticleRepository repository = new ArticleRepository(client, embedder);
        MultimediaSemanticSearchService service =
                new MultimediaSemanticSearchService(client, repository, embedder);
        String marker = "MSEM-" + System.nanoTime();
        String section = "MSemSection-" + marker;
        String storageUri = "https://storage.googleapis.com/aorozcoi-gotham-media-browser/media/image/"
                + marker + ".png";
        List<Float> assetVector = toList(embedder.embedText("asset-" + marker));

        ArticleMultimedia asset = ArticleMultimedia
                .uploaded("m-" + marker, MediaType.IMAGE, storageUri, "image/png", 0, marker + ".png", 12L)
                .withAssetVector(assetVector);

        Article created = null;
        try {
            created = repository.create(Article.newArticle(
                    "City hall gallery " + marker, null, "Summary", "Body",
                    "slug-" + marker, ArticleStatus.PUBLISHED, "en",
                    Instant.parse("2026-03-01T00:00:00Z"),
                    new ArticleMetadata(section, List.of(marker), null, null, null, null, null, null),
                    List.of()).withMultimedia(List.of(asset)));

            MultimediaSearchPage page = service.search(new MultimediaFullTextQuery(
                    "council chamber",
                    List.of(),
                    List.of(ArticleStatus.PUBLISHED),
                    section,
                    "en",
                    null,
                    null,
                    List.of(MediaType.IMAGE),
                    null,
                    1,
                    25));

            assertThat(page.total()).isGreaterThanOrEqualTo(1);
            assertThat(page.items()).extracting(hit -> hit.media().multimediaElementId())
                    .contains("m-" + marker);
            MultimediaSearchHit card = page.items().stream()
                    .filter(hit -> ("m-" + marker).equals(hit.media().multimediaElementId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(card.articleId()).isEqualTo(created.id());
            assertThat(card.media().storageUri()).isEqualTo(storageUri);
        } finally {
            if (created != null && created.id() != null) {
                repository.deleteById(created.id());
            }
        }
    }

    private static List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
