package com.gotham.newsmediabrowser.common.article;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.RRFRetrieverEntry;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.gotham.newsmediabrowser.common.error.DependencyException;
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
 * Article hybrid search ({@code docs/elasticsearch-search-methods.md} §6): reciprocal-rank fusion
 * (RRF) of a BM25 leg and a semantic (kNN) leg. Both legs share the same filters, so a filter
 * removes a document from the fused result regardless of which signal ranked it.
 *
 * <p>Leg composition reuses the standalone services: the BM25 query comes from
 * {@link ArticleFullTextService#buildQuery} and the kNN filters/embedding from
 * {@link ArticleSemanticSearchService}. Both legs are wrapped as {@code standard} retrievers so the
 * shared filters ride inside each leg's {@code bool.filter} — the client's {@code KnnRetriever} has
 * no {@code filter} option, so the kNN leg uses a {@code knn} <em>query</em> instead.
 */
@Service
public class ArticleHybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(ArticleHybridSearchService.class);
    private static final String INDEX = IndexDefinition.MEDIA_BROWSER.indexName();
    private static final String ELASTICSEARCH = "Elasticsearch";
    private static final String VECTOR_FIELD = "article_embedding";
    private static final List<String> VECTOR_FIELDS = List.of("article_embedding", "multimedia.asset_vector");

    /** RRF tunables (cookbook §11). */
    private static final int RANK_WINDOW_MIN = 50;
    private static final int RANK_CONSTANT = 60;
    private static final int NUM_CANDIDATES_FLOOR = 100;
    private static final int NUM_CANDIDATES_FACTOR = 4;

    private final ElasticsearchClient client;
    private final ArticleRepository articleRepository;
    private final ArticleFullTextService articleFullTextService;
    private final ArticleSemanticSearchService articleSemanticSearchService;

    public ArticleHybridSearchService(ElasticsearchClient client,
                                      ArticleRepository articleRepository,
                                      ArticleFullTextService articleFullTextService,
                                      ArticleSemanticSearchService articleSemanticSearchService) {
        this.client = client;
        this.articleRepository = articleRepository;
        this.articleFullTextService = articleFullTextService;
        this.articleSemanticSearchService = articleSemanticSearchService;
    }

    /**
     * Runs article hybrid (RRF) search. Blank {@code q} is rejected; an ImageBind failure or a
     * malformed embedding, and any Elasticsearch failure, become a {@link DependencyException}.
     */
    public ArticlePage search(ArticleFullTextQuery query) {
        requireQuery(query);
        float[] queryVector = articleSemanticSearchService.embedQuery(query.q());
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

    /** Package-private so unit tests can assert the cookbook RRF DSL without a live cluster. */
    SearchRequest buildSearchRequest(ArticleFullTextQuery query, float[] queryVector) {
        requireQuery(query);
        int size = SearchPagination.normalizeSize(query.size());
        int page = SearchPagination.normalizePage(query.page());
        int from = SearchPagination.from(page, size);
        // Fuse a window at least as deep as the requested page so the current slice is covered.
        int rankWindow = Math.max(RANK_WINDOW_MIN, from + size);
        int k = from + size;
        int numCandidates = Math.max(NUM_CANDIDATES_FLOOR, NUM_CANDIDATES_FACTOR * k);

        Query bm25Leg = articleFullTextService.buildQuery(query);
        Query knnLeg = knnLeg(queryVector, numCandidates, articleSemanticSearchService.buildFilters(query));

        SearchRequest request = new SearchRequest.Builder()
                .index(INDEX)
                .from(from)
                .size(size)
                .trackTotalHits(track -> track.enabled(true))
                .source(src -> src.filter(f -> f.excludes(VECTOR_FIELDS)))
                .retriever(r -> r.rrf(rrf -> rrf
                        .retrievers(List.of(
                                RRFRetrieverEntry.of(e -> e.retriever(sr -> sr.standard(s -> s.query(bm25Leg)))),
                                RRFRetrieverEntry.of(e -> e.retriever(sr -> sr.standard(s -> s.query(knnLeg))))))
                        .rankWindowSize(rankWindow)
                        .rankConstant(RANK_CONSTANT)))
                .build();
        log.debug("Article hybrid from={} size={} rankWindow={} numCandidates={}",
                from, size, rankWindow, numCandidates);
        return request;
    }

    private static Query knnLeg(float[] queryVector, int numCandidates, List<Query> filters) {
        List<Float> vector = toVectorList(queryVector);
        Query knn = Query.of(q -> q.knn(k -> k
                .field(VECTOR_FIELD)
                .queryVector(vector)
                .numCandidates(numCandidates)));
        return Query.of(q -> q.bool(b -> {
            b.must(knn);
            if (!filters.isEmpty()) {
                b.filter(filters);
            }
            return b;
        }));
    }

    private static void requireQuery(ArticleFullTextQuery query) {
        if (query == null || query.q() == null || query.q().isBlank()) {
            throw new IllegalArgumentException("Enter a search query.");
        }
    }

    private static List<Float> toVectorList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value != null ? (Map<String, Object>) value : Map.of();
    }
}
