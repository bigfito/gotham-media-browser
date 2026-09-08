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
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import com.gotham.newsmediabrowser.common.media.MediaType;
import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for nested multimedia FTS + {@code inner_hits} (cookbook §7). Live ranking is covered
 * by {@code MultimediaFullTextServiceIT}.
 */
class MultimediaFullTextServiceTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ArticleRepository repository = new ArticleRepository(client, new StubImageBindClient());
    private final MultimediaFullTextService service = new MultimediaFullTextService(client, repository);

    @Test
    void blankQueryIsRejected() {
        assertThatThrownBy(() -> service.search(MultimediaFullTextQuery.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enter a search query.");
    }

    @Test
    void nestedQueryUsesInnerHitsNamedMatchedMedia() {
        String json = service.buildQuery(MultimediaFullTextQuery.of("council chamber")).toString();

        assertThat(json).contains("\"nested\"");
        assertThat(json).contains("\"path\":\"multimedia\"");
        assertThat(json).contains("\"inner_hits\"");
        assertThat(json).contains("\"name\":\"matched_media\"");
        assertThat(json).contains("\"size\":5");
        assertThat(json).contains("\"multi_match\"");
        assertThat(json).contains("council chamber");
        assertThat(json).contains("multimedia.title");
        assertThat(json).contains("multimedia.caption");
        assertThat(json).contains("multimedia.storage_uri");
        assertThat(json).doesNotContain("multimedia_text");
    }

    @Test
    void mediaTypeFilterLivesInsideTheNestedQuery() {
        MultimediaFullTextQuery query = new MultimediaFullTextQuery(
                "chamber", List.of("multimedia.caption"), List.of(ArticleStatus.PUBLISHED),
                "Politics", "en", null, null, List.of(MediaType.IMAGE, MediaType.VIDEO),
                SearchSort.RELEVANCE, 1, 25);

        String json = service.buildQuery(query).toString();

        assertThat(json).contains("multimedia.media_type");
        assertThat(json).contains("IMAGE").contains("VIDEO");
        assertThat(json).contains("\"status\"").contains("PUBLISHED");
        assertThat(json).contains("Politics");
        assertThat(json).contains("multimedia.caption");
    }

    @Test
    void parentProjectionFieldsAddANonNestedMultiMatch() {
        MultimediaFullTextQuery query = new MultimediaFullTextQuery(
                "chamber", List.of("multimedia.title", "multimedia_text"), List.of(),
                null, null, null, null, List.of(), SearchSort.RELEVANCE, 1, 25);

        String json = service.buildQuery(query).toString();

        assertThat(json).contains("multimedia_text");
        assertThat(json).contains("multimedia.title");
        assertThat(json).contains("\"inner_hits\"");
    }

    @Test
    void paginationClampsSizeAndComputesFrom() {
        SearchRequest request = service.buildSearchRequest(new MultimediaFullTextQuery(
                "q", List.of(), List.of(), null, null, null, null, List.of(),
                SearchSort.RELEVANCE, 2, 7));

        assertThat(request.size()).isEqualTo(25);
        assertThat(request.from()).isEqualTo(25);
        assertThat(request.trackTotalHits().enabled()).isTrue();
        assertThat(request.index()).containsExactly("gotham-media-browser");
        assertThat(request.source().toString()).contains("title");
    }

    @Test
    void searchFlattensInnerHitsIntoCardsWithStorageUri() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Map.class))).thenReturn(innerHitResponse());

        MultimediaSearchPage page = service.search(MultimediaFullTextQuery.of("council chamber"));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        MultimediaSearchHit card = page.items().getFirst();
        assertThat(card.articleId()).isEqualTo("art-1");
        assertThat(card.articleTitle()).isEqualTo("Council night");
        assertThat(card.media().storageUri()).isEqualTo("https://storage.googleapis.com/b/media/image/x.png");
        assertThat(card.media().mediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(card.media().title()).isEqualTo("Chamber photo");
        assertThat(card.media().caption()).isEqualTo("council chamber");
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
