package com.gotham.newsmediabrowser.common.article;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.index.IndexDefinition;
import com.gotham.newsmediabrowser.common.search.FullTextFieldRemap;
import com.gotham.newsmediabrowser.common.search.SearchPagination;
import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Multimedia full-text search ({@code docs/elasticsearch-search-methods.md} §7): nested BM25 with
 * {@code inner_hits.matched_media} so the UI can render asset cards (public {@code storage_uri}).
 */
@Service
public class MultimediaFullTextService {

    private static final Logger log = LoggerFactory.getLogger(MultimediaFullTextService.class);
    private static final String INDEX = IndexDefinition.MEDIA_BROWSER.indexName();
    private static final String SERVICE = "Elasticsearch";

    private final ElasticsearchClient client;
    private final ArticleRepository articleRepository;

    public MultimediaFullTextService(ElasticsearchClient client, ArticleRepository articleRepository) {
        this.client = client;
        this.articleRepository = articleRepository;
    }

    /**
     * Runs nested BM25 search and flattens {@code inner_hits.matched_media} into asset cards.
     * Blank {@code q} is rejected; Elasticsearch failures become a {@link DependencyException}.
     */
    public MultimediaSearchPage search(MultimediaFullTextQuery query) {
        requireQuery(query);
        SearchRequest request = buildSearchRequest(query);
        try {
            SearchResponse<Map> response = client.search(request, Map.class);
            List<MultimediaSearchHit> items = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                items.addAll(MultimediaHitMapper.cards(hit, articleRepository));
            }
            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new MultimediaSearchPage(List.copyOf(items), total);
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    SearchRequest buildSearchRequest(MultimediaFullTextQuery query) {
        requireQuery(query);
        int size = SearchPagination.normalizeSize(query.size());
        int page = SearchPagination.normalizePage(query.page());
        int from = SearchPagination.from(page, size);
        SearchSort sort = query.sort() != null ? query.sort() : SearchSort.RELEVANCE;

        SearchRequest.Builder builder = new SearchRequest.Builder()
                .index(INDEX)
                .from(from)
                .size(size)
                .trackTotalHits(track -> track.enabled(true))
                .source(src -> src.filter(f -> f.includes(MultimediaHitMapper.PARENT_SOURCE_FIELDS)))
                .query(buildQuery(query));
        applySort(builder, sort);
        SearchRequest request = builder.build();
        log.debug("Multimedia FTS from={} size={} sort={}", from, size, sort.param());
        return request;
    }

    Query buildQuery(MultimediaFullTextQuery query) {
        requireQuery(query);
        List<Query> must = new ArrayList<>();
        must.add(nestedMultimediaQuery(query));
        List<String> parentFields = FullTextFieldRemap.parentMultimediaFields(query.fields());
        if (!parentFields.isEmpty()) {
            must.add(Query.of(q -> q.multiMatch(m -> m
                    .query(query.q().strip())
                    .fields(parentFields)
                    .type(TextQueryType.BestFields)
                    .operator(Operator.And))));
        }

        List<Query> filters = new ArrayList<>();
        addStatusFilter(filters, query.statuses());
        addTermFilter(filters, "section", query.section());
        addTermFilter(filters, "language", query.language());
        addPublishedRange(filters, query);

        return Query.of(q -> q.bool(b -> {
            b.must(must);
            if (!filters.isEmpty()) {
                b.filter(filters);
            }
            return b;
        }));
    }

    private Query nestedMultimediaQuery(MultimediaFullTextQuery query) {
        List<String> nestedFields = FullTextFieldRemap.nestedMultimediaFields(query.fields());
        Query multiMatch = Query.of(q -> q.multiMatch(m -> m
                .query(query.q().strip())
                .fields(nestedFields)
                .type(TextQueryType.BestFields)
                .operator(Operator.And)));

        List<Query> nestedFilters = new ArrayList<>();
        if (query.mediaTypes() != null && !query.mediaTypes().isEmpty()) {
            List<FieldValue> values = query.mediaTypes().stream()
                    .map(type -> FieldValue.of(type.name()))
                    .toList();
            nestedFilters.add(Query.of(q -> q.terms(t -> t
                    .field("multimedia.media_type")
                    .terms(tf -> tf.value(values)))));
        }

        Query nestedBody = Query.of(q -> q.bool(b -> {
            b.must(multiMatch);
            if (!nestedFilters.isEmpty()) {
                b.filter(nestedFilters);
            }
            return b;
        }));

        return Query.of(q -> q.nested(n -> n
                .path("multimedia")
                .query(nestedBody)
                .innerHits(ih -> ih
                        .name(MultimediaHitMapper.INNER_HITS_NAME)
                        .size(MultimediaHitMapper.INNER_HITS_SIZE)
                        .source(src -> src.filter(f -> f.includes(MultimediaHitMapper.INNER_SOURCE_FIELDS))))));
    }

    private static void requireQuery(MultimediaFullTextQuery query) {
        if (query == null || query.q() == null || query.q().isBlank()) {
            throw new IllegalArgumentException("Enter a search query.");
        }
    }

    private static void addStatusFilter(List<Query> filters, List<ArticleStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        List<FieldValue> values = statuses.stream().map(status -> FieldValue.of(status.name())).toList();
        filters.add(Query.of(q -> q.terms(t -> t.field("status").terms(tf -> tf.value(values)))));
    }

    private static void addTermFilter(List<Query> filters, String field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        filters.add(Query.of(q -> q.term(t -> t.field(field).value(value.strip()))));
    }

    private static void addPublishedRange(List<Query> filters, MultimediaFullTextQuery query) {
        if (query.publishedFrom() == null && query.publishedTo() == null) {
            return;
        }
        filters.add(Query.of(q -> q.range(r -> r.date(d -> {
            d.field("published_at");
            if (query.publishedFrom() != null) {
                d.gte(query.publishedFrom().toString());
            }
            if (query.publishedTo() != null) {
                d.lte(query.publishedTo().toString());
            }
            return d;
        }))));
    }

    private static void applySort(SearchRequest.Builder builder, SearchSort sort) {
        switch (sort) {
            case PUBLISHED_AT_DESC -> builder.sort(s -> s.field(f -> f.field("published_at").order(SortOrder.Desc)));
            case PUBLISHED_AT_ASC -> builder.sort(s -> s.field(f -> f.field("published_at").order(SortOrder.Asc)));
            case TITLE_ASC -> builder.sort(s -> s.field(f -> f.field("title.keyword").order(SortOrder.Asc)));
            case RELEVANCE -> {
            }
        }
    }
}
