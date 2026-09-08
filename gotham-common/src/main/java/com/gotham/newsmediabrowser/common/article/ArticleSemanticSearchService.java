package com.gotham.newsmediabrowser.common.article;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.index.IndexDefinition;
import com.gotham.newsmediabrowser.common.search.SearchPagination;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Article semantic search ({@code docs/elasticsearch-search-methods.md} §5): the query text is
 * embedded with ImageBind and matched against the {@code article_embedding} dense vector via a
 * top-level {@code knn} clause. Ranking is by vector similarity, so the UI {@code fields} and
 * {@code sort} inputs do not apply here — only the shared filters do.
 */
@Service
public class ArticleSemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(ArticleSemanticSearchService.class);
    private static final String INDEX = IndexDefinition.MEDIA_BROWSER.indexName();
    private static final String ELASTICSEARCH = "Elasticsearch";
    private static final String IMAGEBIND = "ImageBind";
    private static final String VECTOR_FIELD = "article_embedding";
    /** Heavy vectors are never needed in the result list. */
    private static final List<String> VECTOR_FIELDS = List.of("article_embedding", "multimedia.asset_vector");

    /** kNN candidate tunables (cookbook §11): {@code num_candidates = max(100, 4 * k)}. */
    private static final int NUM_CANDIDATES_FLOOR = 100;
    private static final int NUM_CANDIDATES_FACTOR = 4;

    private final ElasticsearchClient client;
    private final ArticleRepository articleRepository;
    private final ImageBindClient imageBindClient;

    public ArticleSemanticSearchService(ElasticsearchClient client,
                                        ArticleRepository articleRepository,
                                        ImageBindClient imageBindClient) {
        this.client = client;
        this.articleRepository = articleRepository;
        this.imageBindClient = imageBindClient;
    }

    /**
     * Runs article semantic (kNN) search. Blank {@code q} is rejected; an ImageBind failure or a
     * malformed embedding, and any Elasticsearch failure, become a {@link DependencyException}.
     */
    public ArticlePage search(ArticleFullTextQuery query) {
        requireQuery(query);
        float[] queryVector = embedQuery(query.q());
        SearchRequest request = buildSearchRequest(query, queryVector);
        try {
            SearchResponse<Map> response = client.search(request, Map.class);
            List<Article> items = response.hits().hits().stream()
                    .map(hit -> articleRepository.fromSource(hit.id(), asMap(hit.source())))
                    .toList();
            long total = response.hits().total() != null ? response.hits().total().value() : items.size();
            return new ArticlePage(items, total);
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(ELASTICSEARCH, e);
        }
    }

    /** Embeds the query text and guards the {@value ImageBindClient#EMBEDDING_DIM}-d contract. */
    float[] embedQuery(String q) {
        try {
            float[] vector = imageBindClient.embedText(q.strip());
            requireEmbeddingDim(vector);
            return vector;
        } catch (DependencyException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DependencyException(IMAGEBIND, e);
        }
    }

    /** Package-private so unit tests can assert the cookbook kNN DSL without a live cluster. */
    SearchRequest buildSearchRequest(ArticleFullTextQuery query, float[] queryVector) {
        requireQuery(query);
        requireEmbeddingDim(queryVector);
        int size = SearchPagination.normalizeSize(query.size());
        int page = SearchPagination.normalizePage(query.page());
        int from = SearchPagination.from(page, size);
        // k must cover every hit up to the requested page (from + size), not just one page's worth,
        // or deep pages would come back empty. num_candidates widens the ANN search per §11.
        int k = from + size;
        int numCandidates = Math.max(NUM_CANDIDATES_FLOOR, NUM_CANDIDATES_FACTOR * k);
        List<Float> vector = toVectorList(queryVector);
        List<Query> filters = buildFilters(query);

        SearchRequest request = new SearchRequest.Builder()
                .index(INDEX)
                .from(from)
                .size(size)
                .trackTotalHits(track -> track.enabled(true))
                .source(src -> src.filter(f -> f.excludes(VECTOR_FIELDS)))
                .knn(knn -> knn
                        .field(VECTOR_FIELD)
                        .queryVector(vector)
                        .k(k)
                        .numCandidates(numCandidates)
                        .filter(filters))
                .build();
        log.debug("Article semantic from={} size={} k={} numCandidates={}", from, size, k, numCandidates);
        return request;
    }

    /** Package-private bool filters shared with full-text: status/section/language/range + byline. */
    List<Query> buildFilters(ArticleFullTextQuery query) {
        List<Query> filters = new ArrayList<>();
        addStatusFilter(filters, query.statuses());
        addTermFilter(filters, "section", query.section());
        addTermFilter(filters, "language", query.language());
        addPublishedRange(filters, query);
        addJournalistFilter(filters, query.journalist());
        return filters;
    }

    private static void requireQuery(ArticleFullTextQuery query) {
        if (query == null || query.q() == null || query.q().isBlank()) {
            throw new IllegalArgumentException("Enter a search query.");
        }
    }

    private void requireEmbeddingDim(float[] vector) {
        if (vector == null || vector.length != ImageBindClient.EMBEDDING_DIM) {
            int actual = vector == null ? -1 : vector.length;
            throw new DependencyException(IMAGEBIND, new IllegalStateException(
                    "Expected a " + ImageBindClient.EMBEDDING_DIM + "-d embedding but got length " + actual));
        }
    }

    private static List<Float> toVectorList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
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
        if (ArticleFullTextService.looksLikeJournalistId(value)) {
            filters.add(Query.of(q -> q.nested(n -> n
                    .path("journalists")
                    .query(nq -> nq.term(t -> t.field("journalists.journalist_id").value(value))))));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value != null ? (Map<String, Object>) value : Map.of();
    }
}
