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
 * Unit tests for multimedia semantic (nested kNN + {@code inner_hits}) query construction
 * (cookbook §8), the 1024-d embedding guard, and card flattening. Live ranking is covered in P9-T03.
 */
class MultimediaSemanticSearchServiceTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ImageBindClient imageBind = new StubImageBindClient();
    private final ArticleRepository repository = new ArticleRepository(client, imageBind);
    private final MultimediaSemanticSearchService service =
            new MultimediaSemanticSearchService(client, repository, imageBind);

    private static float[] vector() {
        return new float[ImageBindClient.EMBEDDING_DIM];
    }

    private SearchRequest request(List<MediaType> mediaTypes, int page, int size) {
        return service.buildSearchRequest(vector(), List.of(), null, null, null, null, mediaTypes, page, size);
    }

    @Test
    void blankQueryIsRejected() {
        assertThatThrownBy(() -> service.search(MultimediaFullTextQuery.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enter a search query.");
    }

    @Test
    void buildsNestedKnnOnAssetVectorWithMatchedMediaInnerHits() {
        SearchRequest request = request(List.of(), 1, 25);
        // The vector is nested, so kNN runs as a knn *query* inside a nested query (not the
        // top-level knn option, which leaves inner_hits empty for nested vectors).
        assertThat(request.knn()).isEmpty();
        String json = request.query().toString();

        assertThat(json).contains("\"nested\"");
        assertThat(json).contains("\"path\":\"multimedia\"");
        assertThat(json).contains("\"knn\"");
        assertThat(json).contains("multimedia.asset_vector");
        assertThat(json).contains("\"num_candidates\":100");
        assertThat(json).contains("\"inner_hits\"");
        assertThat(json).contains("\"name\":\"matched_media\"");
        assertThat(json).contains("\"size\":5");
        assertThat(json).contains("multimedia.storage_uri");
    }

    @Test
    void parentSourceIsRestrictedAndCandidatesCoverDeepPages() {
        SearchRequest deep = request(List.of(), 3, 50);
        assertThat(deep.from()).isEqualTo(100);
        // num_candidates = max(100, 4 * (from + size)) = 4 * 150 = 600.
        assertThat(deep.query().toString()).contains("\"num_candidates\":600");
        assertThat(deep.source().toString()).contains("title").contains("slug");
    }

    @Test
    void mediaTypeFilterIsANestedTermsFilterAlongsideTheKnn() {
        String json = request(List.of(MediaType.IMAGE, MediaType.VIDEO), 1, 25).query().toString();

        assertThat(json).contains("\"knn\"");
        assertThat(json).contains("multimedia.media_type");
        assertThat(json).contains("IMAGE").contains("VIDEO");
    }

    @Test
    void parentFiltersAreTopLevelBoolFilters() {
        String json = service.buildSearchRequest(
                        vector(), List.of(ArticleStatus.PUBLISHED), "Politics", "en", null, null, List.of(), 1, 25)
                .query().toString();

        assertThat(json).contains("\"status\"").contains("PUBLISHED");
        assertThat(json).contains("\"section\"").contains("Politics");
        assertThat(json).contains("\"language\"").contains("\"en\"");
    }

    @Test
    void nonConformingEmbeddingIsRejectedAsDependencyFailure() {
        assertThatThrownBy(() -> service.buildSearchRequest(
                        new float[3], List.of(), null, null, null, null, List.of(), 1, 25))
                .isInstanceOf(DependencyException.class)
                .extracting(ex -> ((DependencyException) ex).getServiceName())
                .isEqualTo("ImageBind");
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
        MultimediaSemanticSearchService svc = new MultimediaSemanticSearchService(client, repository, failing);

        assertThatThrownBy(() -> svc.search(MultimediaFullTextQuery.of("chamber")))
                .isInstanceOf(DependencyException.class)
                .extracting(ex -> ((DependencyException) ex).getServiceName())
                .isEqualTo("ImageBind");
    }

    @Test
    void searchFlattensInnerHitsIntoCards() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Map.class))).thenReturn(innerHitResponse());

        MultimediaSearchPage page = service.search(MultimediaFullTextQuery.of("council chamber"));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        MultimediaSearchHit card = page.items().getFirst();
        assertThat(card.articleId()).isEqualTo("art-1");
        assertThat(card.media().storageUri()).isEqualTo("https://storage.googleapis.com/b/media/image/x.png");
        assertThat(card.media().mediaType()).isEqualTo(MediaType.IMAGE);
    }

    @Test
    void searchWrapsElasticsearchFailure() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("down"));

        assertThatThrownBy(() -> service.search(MultimediaFullTextQuery.of("q")))
                .isInstanceOf(DependencyException.class)
                .extracting(ex -> ((DependencyException) ex).getServiceName())
                .isEqualTo("Elasticsearch");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SearchResponse<Map> innerHitResponse() {
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
                                        .hits(innerHit -> innerHit
                                                .index("gotham-media-browser")
                                                .id("art-1")
                                                .source(JsonData.of(nested))))))));
    }
}
