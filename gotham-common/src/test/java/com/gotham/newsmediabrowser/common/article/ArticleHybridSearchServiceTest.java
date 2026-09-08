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
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import com.gotham.newsmediabrowser.common.media.MediaType;
import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for article hybrid RRF query construction (cookbook §6): both legs present, shared
 * filters, tunables, and failure mapping. Live fused ranking is covered in P9-T03.
 */
class ArticleHybridSearchServiceTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ImageBindClient imageBind = new StubImageBindClient();
    private final ArticleRepository repository = new ArticleRepository(client, imageBind);
    private final ArticleFullTextService fullText = new ArticleFullTextService(client, repository);
    private final ArticleSemanticSearchService semantic =
            new ArticleSemanticSearchService(client, repository, imageBind);
    private final ArticleHybridSearchService service =
            new ArticleHybridSearchService(client, repository, fullText, semantic);

    private static float[] vector() {
        return new float[ImageBindClient.EMBEDDING_DIM];
    }

    private static ArticleFullTextQuery query(int page, int size) {
        return new ArticleFullTextQuery(
                "transit funding", List.of(), List.of(), null, null, null, null, null,
                SearchSort.RELEVANCE, page, size);
    }

    @Test
    void blankQueryIsRejected() {
        assertThatThrownBy(() -> service.search(ArticleFullTextQuery.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enter a search query.");
    }

    @Test
    void buildsRrfOfBm25AndKnnLegs() {
        SearchRequest request = service.buildSearchRequest(query(1, 25), vector());
        // RRF replaces the top-level query/knn.
        assertThat(request.query()).isNull();
        assertThat(request.knn()).isEmpty();
        String json = request.retriever().toString();

        assertThat(json).contains("\"rrf\"");
        assertThat(json).contains("\"rank_constant\":60");
        assertThat(json).contains("\"rank_window_size\":50");
        assertThat(json).contains("\"multi_match\"");
        assertThat(json).contains("\"knn\"");
        assertThat(json).contains("article_embedding");
        assertThat(json).contains("\"num_candidates\":100");
        assertThat(request.index()).containsExactly("gotham-media-browser");
        assertThat(request.trackTotalHits().enabled()).isTrue();
    }

    @Test
    void rankWindowAndCandidatesCoverDeepPages() {
        SearchRequest deep = service.buildSearchRequest(query(3, 50), vector());
        assertThat(deep.from()).isEqualTo(100);
        assertThat(deep.size()).isEqualTo(50);
        String json = deep.retriever().toString();
        // rank_window_size = max(50, from + size) = 150; num_candidates = 4 * 150 = 600.
        assertThat(json).contains("\"rank_window_size\":150");
        assertThat(json).contains("\"num_candidates\":600");
    }

    @Test
    void sharedFiltersRideBothLegs() {
        ArticleFullTextQuery filtered = new ArticleFullTextQuery(
                "transit funding", List.of(), List.of(ArticleStatus.PUBLISHED), "Politics", "en",
                null, null, "j_lois_lane", SearchSort.RELEVANCE, 1, 25);

        String json = service.buildSearchRequest(filtered, vector()).retriever().toString();

        // Status/section/language appear in both the BM25 and the kNN leg's bool filter.
        assertThat(json).contains("PUBLISHED");
        assertThat(json).contains("Politics");
        assertThat(json).contains("\"language\"");
        assertThat(json).contains("journalists.journalist_id");
    }

    @Test
    void resultListExcludesHeavyVectors() {
        String json = service.buildSearchRequest(query(1, 25), vector()).source().toString();
        assertThat(json).contains("article_embedding").contains("multimedia.asset_vector").contains("excludes");
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
        ArticleSemanticSearchService failingSemantic =
                new ArticleSemanticSearchService(client, repository, failing);
        ArticleHybridSearchService svc =
                new ArticleHybridSearchService(client, repository, fullText, failingSemantic);

        assertThatThrownBy(() -> svc.search(query(1, 25)))
                .isInstanceOf(DependencyException.class)
                .extracting(ex -> ((DependencyException) ex).getServiceName())
                .isEqualTo("ImageBind");
    }

    @Test
    void searchMapsHitsAndTotal() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Map.class))).thenReturn(oneHitResponse());

        ArticlePage page = service.search(query(1, 25));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().id()).isEqualTo("art-1");
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
    private static SearchResponse<Map> oneHitResponse() {
        Map source = Map.of(
                "title", "Bat-signal returns",
                "status", "PUBLISHED",
                "tags", List.of(),
                "journalists", List.of(),
                "multimedia", List.of());
        return SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(ShardStatistics.of(s -> s.total(1).successful(1).failed(0)))
                .hits(h -> h
                        .total(t -> t.value(1).relation(TotalHitsRelation.Eq))
                        .hits(hit -> hit.index("gotham-media-browser").id("art-1").source(source))));
    }
}
