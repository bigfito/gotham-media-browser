package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.json.JsonData;
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for multimedia hybrid RRF query construction (cookbook §9): nested BM25 + nested kNN
 * legs with distinct {@code inner_hits} names, and card merging. Live fused ranking is in P9-T03.
 */
class MultimediaHybridSearchServiceTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ImageBindClient imageBind = new StubImageBindClient();
    private final ArticleRepository repository = new ArticleRepository(client, imageBind);
    private final MultimediaFullTextService fullText = new MultimediaFullTextService(client, repository);
    private final MultimediaSemanticSearchService semantic =
            new MultimediaSemanticSearchService(client, repository, imageBind);
    private final MultimediaHybridSearchService service =
            new MultimediaHybridSearchService(client, repository, fullText, semantic);

    private static float[] vector() {
        return new float[ImageBindClient.EMBEDDING_DIM];
    }

    private static MultimediaFullTextQuery query(int page, int size) {
        return new MultimediaFullTextQuery(
                "council chamber", List.of(), List.of(), null, null, null, null, List.of(),
                null, page, size);
    }

    @Test
    void blankQueryIsRejected() {
        assertThatThrownBy(() -> service.search(MultimediaFullTextQuery.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enter a search query.");
    }

    @Test
    void buildsRrfOfNestedBm25AndNestedKnnWithDistinctInnerHitsNames() {
        SearchRequest request = service.buildSearchRequest(query(1, 25), vector());
        assertThat(request.query()).isNull();
        assertThat(request.knn()).isEmpty();
        String json = request.retriever().toString();

        assertThat(json).contains("\"rrf\"");
        assertThat(json).contains("\"rank_constant\":60");
        assertThat(json).contains("\"nested\"");
        assertThat(json).contains("\"path\":\"multimedia\"");
        assertThat(json).contains("\"multi_match\"");
        assertThat(json).contains("\"knn\"");
        assertThat(json).contains("multimedia.asset_vector");
        // Distinct inner-hits names avoid the RRF duplicate-key rejection.
        assertThat(json).contains("\"name\":\"matched_media\"");
        assertThat(json).contains("\"name\":\"matched_media_knn\"");
    }

    @Test
    void rankWindowCoversDeepPagesAndParentSourceRestricted() {
        SearchRequest deep = service.buildSearchRequest(query(3, 50), vector());
        assertThat(deep.from()).isEqualTo(100);
        assertThat(deep.retriever().toString()).contains("\"rank_window_size\":150");
        assertThat(deep.source().toString()).contains("title").contains("slug");
    }

    @Test
    void mediaTypeFilterAppliesInsideTheKnnLeg() {
        MultimediaFullTextQuery typed = new MultimediaFullTextQuery(
                "council chamber", List.of(), List.of(), null, null, null, null,
                List.of(MediaType.IMAGE), null, 1, 25);
        String json = service.buildSearchRequest(typed, vector()).retriever().toString();

        assertThat(json).contains("multimedia.media_type");
        assertThat(json).contains("IMAGE");
    }

    @Test
    void imageBindFailureBecomesDependencyException() {
        ImageBindClient failing = new ImageBindClient() {
            @Override
            public float[] embedText(String text) {
                throw new IllegalStateException("imagebind down");
            }

            @Override
            public float[] embedMedia(MediaType type, byte[] data, String filename, String contentType) {
                throw new UnsupportedOperationException();
            }
        };
        MultimediaSemanticSearchService failingSemantic =
                new MultimediaSemanticSearchService(client, repository, failing);
        MultimediaHybridSearchService svc =
                new MultimediaHybridSearchService(client, repository, fullText, failingSemantic);

        assertThatThrownBy(() -> svc.search(query(1, 25)))
                .isInstanceOf(DependencyException.class)
                .extracting(ex -> ((DependencyException) ex).getServiceName())
                .isEqualTo("ImageBind");
    }

    @Test
    void searchMergesCardsFromBothLegsDeduplicatingByAssetId() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Map.class))).thenReturn(bothLegsResponse());

        MultimediaSearchPage page = service.search(query(1, 25));

        // The same asset id is present in both legs' inner hits — it renders once.
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().media().multimediaElementId()).isEqualTo("m1");
        assertThat(page.items().getFirst().media().storageUri())
                .isEqualTo("https://storage.googleapis.com/b/media/image/x.png");
    }

    @Test
    void searchWrapsElasticsearchFailure() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("down"));

        assertThatThrownBy(() -> service.search(query(1, 25)))
                .isInstanceOf(DependencyException.class)
                .extracting(ex -> ((DependencyException) ex).getServiceName())
                .isEqualTo("Elasticsearch");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SearchResponse<Map> bothLegsResponse() {
        Map parent = Map.of("title", "Council night", "status", "PUBLISHED", "section", "Politics");
        Map nested = Map.of(
                "multimedia_element_id", "m1",
                "media_type", "IMAGE",
                "storage_uri", "https://storage.googleapis.com/b/media/image/x.png",
                "mime_type", "image/png",
                "position", 0,
                "title", "Chamber photo",
                "caption", "council chamber");
        return SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(ShardStatistics.of(s -> s.total(1).successful(1).failed(0)))
                .hits(h -> h
                        .total(t -> t.value(1).relation(TotalHitsRelation.Eq))
                        .hits(hit -> hit
                                .index("gotham-media-browser")
                                .id("art-1")
                                .source(parent)
                                .innerHits("matched_media", ih -> ih.hits(inner -> inner
                                        .hits(innerHit -> innerHit.index("gotham-media-browser").id("art-1")
                                                .source(JsonData.of(nested)))))
                                .innerHits("matched_media_knn", ih -> ih.hits(inner -> inner
                                        .hits(innerHit -> innerHit.index("gotham-media-browser").id("art-1")
                                                .source(JsonData.of(nested))))))));
    }
}
