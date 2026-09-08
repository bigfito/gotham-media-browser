package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for article semantic (kNN) query construction (cookbook §5), the 1024-d embedding
 * guard, and Elasticsearch failure mapping. Live ranking is covered in P9-T03.
 */
class ArticleSemanticSearchServiceTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ImageBindClient imageBind = new StubImageBindClient();
    private final ArticleRepository repository = new ArticleRepository(client, imageBind);
    private final ArticleSemanticSearchService service =
            new ArticleSemanticSearchService(client, repository, imageBind);

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
    void buildsKnnOnArticleEmbeddingWith1024dVector() {
        SearchRequest request = service.buildSearchRequest(query(1, 25), vector());

        assertThat(request.knn()).hasSize(1);
        KnnSearch knn = request.knn().getFirst();
        assertThat(knn.field()).isEqualTo("article_embedding");
        assertThat(knn.queryVector()).hasSize(1024);
        assertThat(request.index()).containsExactly("gotham-media-browser");
        assertThat(request.trackTotalHits().enabled()).isTrue();
    }

    @Test
    void kCoversRequestedPageAndNumCandidatesFollowsCookbook() {
        // Page 1, size 25 → k = 25, num_candidates = max(100, 4*25) = 100.
        KnnSearch first = service.buildSearchRequest(query(1, 25), vector()).knn().getFirst();
        assertThat(first.k()).isEqualTo(25);
        assertThat(first.numCandidates()).isEqualTo(100);

        // Page 3, size 50 → from = 100, k = 150, num_candidates = 4*150 = 600.
        SearchRequest deep = service.buildSearchRequest(query(3, 50), vector());
        assertThat(deep.from()).isEqualTo(100);
        assertThat(deep.size()).isEqualTo(50);
        assertThat(deep.knn().getFirst().k()).isEqualTo(150);
        assertThat(deep.knn().getFirst().numCandidates()).isEqualTo(600);
    }

    @Test
    void invalidPageSizeFallsBackTo25() {
        SearchRequest request = service.buildSearchRequest(query(1, 7), vector());
        assertThat(request.size()).isEqualTo(25);
        assertThat(request.knn().getFirst().k()).isEqualTo(25);
    }

    @Test
    void appliesStatusSectionLanguageRangeAndJournalistFiltersInsideKnn() {
        ArticleFullTextQuery filtered = new ArticleFullTextQuery(
                "transit funding",
                List.of(),
                List.of(ArticleStatus.PUBLISHED, ArticleStatus.DRAFT),
                "Politics",
                "en",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-09-06T23:59:59Z"),
                "j_lois_lane",
                SearchSort.RELEVANCE, 1, 25);

        String json = service.buildSearchRequest(filtered, vector()).knn().getFirst().filter().toString();

        assertThat(json).contains("\"status\"").contains("PUBLISHED").contains("DRAFT");
        assertThat(json).contains("\"section\"").contains("Politics");
        assertThat(json).contains("\"language\"").contains("\"en\"");
        assertThat(json).contains("\"published_at\"").contains("2026-01-01T00:00:00Z");
        assertThat(json).contains("\"path\":\"journalists\"").contains("journalists.journalist_id");
    }

    @Test
    void freeTextJournalistUsesNameMatchInsideKnnFilter() {
        ArticleFullTextQuery byName = new ArticleFullTextQuery(
                "city hall", List.of(), List.of(), null, null, null, null, "Lois Lane",
                SearchSort.RELEVANCE, 1, 25);

        String json = service.buildSearchRequest(byName, vector()).knn().getFirst().filter().toString();

        assertThat(json).contains("journalists.full_name").contains("Lois Lane");
        assertThat(json).contains("journalist_names");
        assertThat(json).doesNotContain("journalists.journalist_id");
    }

    @Test
    void resultListExcludesHeavyVectors() {
        String json = service.buildSearchRequest(query(1, 25), vector()).source().toString();
        assertThat(json).contains("article_embedding").contains("multimedia.asset_vector");
        assertThat(json).contains("excludes");
    }

    @Test
    void nonConformingEmbeddingIsRejectedAsDependencyFailure() {
        assertThatThrownBy(() -> service.buildSearchRequest(query(1, 25), new float[3]))
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
        ArticleSemanticSearchService svc = new ArticleSemanticSearchService(client, repository, failing);

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
        assertThat(page.items().getFirst().title()).isEqualTo("Bat-signal returns");
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
