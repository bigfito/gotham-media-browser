package com.gotham.newsmediabrowser.web.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.gotham.newsmediabrowser.common.article.Article;
import com.gotham.newsmediabrowser.common.article.ArticleMetadata;
import com.gotham.newsmediabrowser.common.article.ArticleMultimedia;
import com.gotham.newsmediabrowser.common.article.ArticleRepository;
import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.article.MultimediaSearchPage;
import com.gotham.newsmediabrowser.common.article.MultimediaSemanticSearchService;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties.Limit;
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Live file-upload vector search against a real {@code gotham-media-browser} index (P8-T03): an
 * uploaded file is classified, size-checked, embedded (deterministic {@link StubImageBindClient}),
 * and matched with the nested kNN of §10. A unique {@code section} isolates the seeded asset.
 * Skipped unless {@code ES_ENDPOINT} / {@code ES_API_KEY} are set.
 */
@Tag("integration")
class MultimediaVectorSearchServiceIT {

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
    void uploadedFileVectorSearchReturnsMatchedAssetCard() {
        StubImageBindClient embedder = new StubImageBindClient();
        ArticleRepository repository = new ArticleRepository(client, embedder);
        MultimediaSemanticSearchService semantic =
                new MultimediaSemanticSearchService(client, repository, embedder);
        MediaLimitsProperties limits = new MediaLimitsProperties(
                new Limit(DataSize.ofMegabytes(10), null),
                new Limit(DataSize.ofMegabytes(20), Duration.ofMinutes(5)),
                new Limit(DataSize.ofMegabytes(50), Duration.ofSeconds(90)));
        MultimediaVectorSearchService service =
                new MultimediaVectorSearchService(embedder, limits, semantic);

        String marker = "MVEC-" + System.nanoTime();
        String section = "MVecSection-" + marker;
        String storageUri = "https://storage.googleapis.com/aorozcoi-gotham-media-browser/media/image/"
                + marker + ".png";
        List<Float> assetVector = toList(embedder.embedText("asset-" + marker));
        ArticleMultimedia asset = ArticleMultimedia
                .uploaded("m-" + marker, MediaType.IMAGE, storageUri, "image/png", 0, marker + ".png", 12L)
                .withAssetVector(assetVector);

        Article created = null;
        try {
            created = repository.create(Article.newArticle(
                    "Gallery upload " + marker, null, "Summary", "Body",
                    "slug-" + marker, ArticleStatus.PUBLISHED, "en", Instant.parse("2026-03-01T00:00:00Z"),
                    new ArticleMetadata(section, List.of(marker), null, null, null, null, null, null),
                    List.of()).withMultimedia(List.of(asset)));

            MultipartFile upload = new MockMultipartFile("media", "query.png", "image/png",
                    new byte[] {10, 20, 30, 40});
            MultimediaSearchPage page = service.search(upload,
                    List.of(ArticleStatus.PUBLISHED), section, "en", null, null, List.of(MediaType.IMAGE), 1, 25);

            assertThat(page.total()).isGreaterThanOrEqualTo(1);
            assertThat(page.items()).extracting(hit -> hit.media().multimediaElementId())
                    .contains("m-" + marker);
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
