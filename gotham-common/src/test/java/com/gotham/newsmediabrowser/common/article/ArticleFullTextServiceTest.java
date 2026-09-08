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
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for article FTS query construction (cookbook §4) and pagination. Live hit ranking is
 * covered by {@code ArticleFullTextServiceIT}.
 */
class ArticleFullTextServiceTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ArticleRepository repository = new ArticleRepository(client, new StubImageBindClient());
    private final ArticleFullTextService service = new ArticleFullTextService(client, repository);

    @Test
    void blankQueryIsRejected() {
        assertThatThrownBy(() -> service.search(ArticleFullTextQuery.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enter a search query.");
    }

    @Test
    void defaultQueryIsBestFieldsAndMultiMatchOnCoreTextFields() {
        String json = service.buildQuery(ArticleFullTextQuery.of("transit funding")).toString();

        assertThat(json).contains("\"multi_match\"");
        assertThat(json).contains("\"query\":\"transit funding\"");
        assertThat(json).contains("\"type\":\"best_fields\"");
        assertThat(json).contains("\"operator\":\"and\"");
        assertThat(json).contains("\"title\"", "\"subtitle\"", "\"summary\"", "\"body\"");
        assertThat(json).doesNotContain("\"journalists\"");
    }

    @Test
    void remapsSectionCheckboxToAnalyzableSubfield() {
        ArticleFullTextQuery query = new ArticleFullTextQuery(
                "budget", List.of("section", "title"), List.of(), null, null, null, null, null,
                SearchSort.RELEVANCE, 1, 25);

        String json = service.buildQuery(query).toString();

        assertThat(json).contains("\"section.text\"");
        assertThat(json).contains("\"title\"");
        assertThat(json).doesNotContain("\"fields\":[\"section\"]");
    }

    @Test
    void appliesStatusSectionLanguageRangeAndJournalistIdFilters() {
        ArticleFullTextQuery query = new ArticleFullTextQuery(
                "transit funding",
                List.of(),
                List.of(ArticleStatus.PUBLISHED, ArticleStatus.DRAFT),
                "Politics",
                "en",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-09-06T23:59:59Z"),
                "j_lois_lane",
                SearchSort.RELEVANCE,
                1,
                25);

        String json = service.buildQuery(query).toString();

        assertThat(json).contains("\"terms\"").contains("\"status\"");
        assertThat(json).contains("PUBLISHED").contains("DRAFT");
        assertThat(json).contains("\"section\"").contains("Politics");
        assertThat(json).contains("\"language\"").contains("\"en\"");
        assertThat(json).contains("\"published_at\"");
        assertThat(json).contains("2026-01-01T00:00:00Z").contains("2026-09-06T23:59:59Z");
        assertThat(json).contains("\"nested\"").contains("\"path\":\"journalists\"");
        assertThat(json).contains("\"journalists.journalist_id\"").contains("j_lois_lane");
        assertThat(json).doesNotContain("journalists.full_name");
    }

    @Test
    void freeTextJournalistUsesNestedNameMatch() {
        ArticleFullTextQuery query = new ArticleFullTextQuery(
                "city hall", List.of(), List.of(), null, null, null, null, "Lois Lane",
                SearchSort.RELEVANCE, 1, 25);

        String json = service.buildQuery(query).toString();

        assertThat(json).contains("\"journalists.full_name\"");
        assertThat(json).contains("Lois Lane");
        assertThat(json).contains("\"journalist_names\"");
        assertThat(json).doesNotContain("journalists.journalist_id");
    }

    @Test
    void paginationClampsSizeAndComputesFrom() {
        ArticleFullTextQuery query = new ArticleFullTextQuery(
                "gotham", List.of(), List.of(), null, null, null, null, null,
                SearchSort.RELEVANCE, 2, 7);

        SearchRequest request = service.buildSearchRequest(query);

        assertThat(request.size()).isEqualTo(25);
        assertThat(request.from()).isEqualTo(25);
        assertThat(request.trackTotalHits()).isNotNull();
        assertThat(request.trackTotalHits().enabled()).isTrue();
        assertThat(request.index()).containsExactly("gotham-media-browser");
    }

    @Test
    void allowedPageSizesAreHonored() {
        assertThat(service.buildSearchRequest(pageSizeQuery(1, 50)).size()).isEqualTo(50);
        assertThat(service.buildSearchRequest(pageSizeQuery(3, 100)).from()).isEqualTo(200);
        assertThat(service.buildSearchRequest(pageSizeQuery(3, 100)).size()).isEqualTo(100);
    }

    @Test
    void fieldSortsAreAppliedOnlyWhenRequested() {
        SearchRequest relevance = service.buildSearchRequest(ArticleFullTextQuery.of("q"));
        assertThat(relevance.sort()).isEmpty();

        SearchRequest byDate = service.buildSearchRequest(new ArticleFullTextQuery(
                "q", List.of(), List.of(), null, null, null, null, null,
                SearchSort.PUBLISHED_AT_DESC, 1, 25));
        assertThat(byDate.sort().toString()).contains("published_at");

        SearchRequest byTitle = service.buildSearchRequest(new ArticleFullTextQuery(
                "q", List.of(), List.of(), null, null, null, null, null,
                SearchSort.TITLE_ASC, 1, 25));
        assertThat(byTitle.sort().toString()).contains("title.keyword");
    }

    @Test
    void searchMapsHitsAndTotal() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Map.class))).thenReturn(oneHitResponse());

        ArticlePage page = service.search(ArticleFullTextQuery.of("bat-signal"));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().id()).isEqualTo("art-1");
        assertThat(page.items().getFirst().title()).isEqualTo("Bat-signal returns");
    }

    @Test
    void searchWrapsElasticsearchFailure() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("down"));

        assertThatThrownBy(() -> service.search(ArticleFullTextQuery.of("q")))
                .isInstanceOf(DependencyException.class)
                .extracting(ex -> ((DependencyException) ex).getServiceName())
                .isEqualTo("Elasticsearch");
    }

    @Test
    void journalistIdHeuristic() {
        assertThat(ArticleFullTextService.looksLikeJournalistId("j_lois_lane")).isTrue();
        assertThat(ArticleFullTextService.looksLikeJournalistId("ABCDEFghij1234567890")).isTrue();
        assertThat(ArticleFullTextService.looksLikeJournalistId("Lois Lane")).isFalse();
        assertThat(ArticleFullTextService.looksLikeJournalistId("Lois")).isFalse();
    }

    private static ArticleFullTextQuery pageSizeQuery(int page, int size) {
        return new ArticleFullTextQuery(
                "q", List.of(), List.of(), null, null, null, null, null, SearchSort.RELEVANCE, page, size);
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
