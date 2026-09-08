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
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.index.IndexDefinition;
import com.gotham.newsmediabrowser.common.search.FullTextFieldRemap;
import com.gotham.newsmediabrowser.common.search.SearchPagination;
import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Article full-text search ({@code docs/elasticsearch-search-methods.md} §4): {@code multi_match}
 * with field remap, parent filters, nested journalist filter, {@code from}/{@code size}, and
 * {@code track_total_hits}.
 */
@Service
public class ArticleFullTextService {

    private static final Logger log = LoggerFactory.getLogger(ArticleFullTextService.class);
    private static final String INDEX = IndexDefinition.MEDIA_BROWSER.indexName();
    private static final String SERVICE = "Elasticsearch";
    private static final List<String> VECTOR_FIELDS = List.of("article_embedding", "multimedia.asset_vector");

    /**
     * Treat as a journalist ES {@code _id} when the value is a {@code j_*} token or a long
     * URL-safe id (typical auto {@code _id} length). Names such as {@code Lois Lane} stay free text.
     */
    private static final Pattern JOURNALIST_ID = Pattern.compile("^(?:j_[A-Za-z0-9_-]+|[A-Za-z0-9_-]{16,})$");

    private final ElasticsearchClient client;
    private final ArticleRepository articleRepository;

    public ArticleFullTextService(ElasticsearchClient client, ArticleRepository articleRepository) {
        this.client = client;
        this.articleRepository = articleRepository;
    }

    /**
     * Runs article BM25 search. Blank {@code q} is rejected; Elasticsearch failures become a
     * {@link DependencyException}.
     */
    public ArticlePage search(ArticleFullTextQuery query) {
        requireQuery(query);
        SearchRequest request = buildSearchRequest(query);
        try {
            SearchResponse<Map> response = client.search(request, Map.class);
            List<Article> items = response.hits().hits().stream()
                    .map(hit -> articleRepository.fromSource(hit.id(), asMap(hit.source())))
                    .toList();
            long total = response.hits().total() != null ? response.hits().total().value() : items.size();
            return new ArticlePage(items, total);
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /** Package-private so unit tests can assert the cookbook DSL without a live cluster. */
    SearchRequest buildSearchRequest(ArticleFullTextQuery query) {
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
                .source(src -> src.filter(f -> f.excludes(VECTOR_FIELDS)))
                .query(buildQuery(query));
        applySort(builder, sort);
        SearchRequest request = builder.build();
        log.debug("Article FTS from={} size={} sort={}", from, size, sort.param());
        return request;
    }

    /** Package-private bool query: must multi_match + optional filters. */
    Query buildQuery(ArticleFullTextQuery query) {
        requireQuery(query);
        List<String> fields = FullTextFieldRemap.articleFields(query.fields());
        Query multiMatch = Query.of(q -> q.multiMatch(m -> m
                .query(query.q().strip())
                .fields(fields)
                .type(TextQueryType.BestFields)
                .operator(Operator.And)));

        List<Query> filters = new ArrayList<>();
        addStatusFilter(filters, query.statuses());
        addTermFilter(filters, "section", query.section());
        addTermFilter(filters, "language", query.language());
        addPublishedRange(filters, query);
        addJournalistFilter(filters, query.journalist());

        return Query.of(q -> q.bool(b -> {
            b.must(multiMatch);
            if (!filters.isEmpty()) {
                b.filter(filters);
            }
            return b;
        }));
    }

    static boolean looksLikeJournalistId(String value) {
        return value != null && JOURNALIST_ID.matcher(value.strip()).matches();
    }

    private static void requireQuery(ArticleFullTextQuery query) {
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

    private static void addPublishedRange(List<Query> filters, ArticleFullTextQuery query) {
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

    private static void addJournalistFilter(List<Query> filters, String journalist) {
        if (journalist == null || journalist.isBlank()) {
            return;
        }
        String value = journalist.strip();
        if (looksLikeJournalistId(value)) {
            filters.add(nestedJournalistTerm(value));
            return;
        }
        Query nestedName = Query.of(q -> q.nested(n -> n
                .path("journalists")
                .query(nq -> nq.match(m -> m.field("journalists.full_name").query(value)))));
        Query parentNames = Query.of(q -> q.match(m -> m.field("journalist_names").query(value)));
        filters.add(Query.of(q -> q.bool(b -> b
                .should(nestedName, parentNames)
                .minimumShouldMatch("1"))));
    }

    private static Query nestedJournalistTerm(String journalistId) {
        return Query.of(q -> q.nested(n -> n
                .path("journalists")
                .query(nq -> nq.term(t -> t.field("journalists.journalist_id").value(journalistId)))));
    }

    private static void applySort(SearchRequest.Builder builder, SearchSort sort) {
        switch (sort) {
            case PUBLISHED_AT_DESC -> builder.sort(s -> s.field(f -> f.field("published_at").order(SortOrder.Desc)));
            case PUBLISHED_AT_ASC -> builder.sort(s -> s.field(f -> f.field("published_at").order(SortOrder.Asc)));
            case TITLE_ASC -> builder.sort(s -> s.field(f -> f.field("title.keyword").order(SortOrder.Asc)));
            case RELEVANCE -> {
                // Score order — omit sort.
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value != null ? (Map<String, Object>) value : Map.of();
    }
}
