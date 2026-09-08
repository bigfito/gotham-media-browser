package com.gotham.newsmediabrowser.common.article;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.RRFRetrieverEntry;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
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
 * Multimedia hybrid search ({@code docs/elasticsearch-search-methods.md} §9): reciprocal-rank fusion
 * (RRF) of a nested BM25 leg and a nested semantic (kNN) leg, both scoped to the {@code multimedia}
 * path and both carrying {@code inner_hits.matched_media} so the UI renders the matched asset cards.
 *
 * <p>Leg composition reuses the standalone services — {@link MultimediaFullTextService#buildQuery}
 * for BM25 and {@link MultimediaSemanticSearchService#nestedKnnQuery} for kNN — wrapped as
 * {@code standard} retrievers so the shared parent filters ride inside each leg's {@code bool.filter}.
 */
@Service
public class MultimediaHybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(MultimediaHybridSearchService.class);
    private static final String INDEX = IndexDefinition.MEDIA_BROWSER.indexName();
    private static final String ELASTICSEARCH = "Elasticsearch";

    /** RRF tunables (cookbook §11). */
    private static final int RANK_WINDOW_MIN = 50;
    private static final int RANK_CONSTANT = 60;
    private static final int NUM_CANDIDATES_FLOOR = 100;
    private static final int NUM_CANDIDATES_FACTOR = 4;

    /**
     * The BM25 leg keeps the default {@code matched_media} name; the kNN leg uses a distinct one.
     * RRF merges both legs' {@code inner_hits} into one map, so the names must differ. Cards from the
     * two blocks are merged and de-duplicated by asset id in {@link MultimediaHitMapper}.
     */
    private static final List<String> HYBRID_INNER_HITS_NAMES =
            List.of(MultimediaHitMapper.INNER_HITS_NAME, MultimediaHitMapper.INNER_HITS_NAME_KNN);

    private final ElasticsearchClient client;
    private final ArticleRepository articleRepository;
    private final MultimediaFullTextService multimediaFullTextService;
    private final MultimediaSemanticSearchService multimediaSemanticSearchService;

    public MultimediaHybridSearchService(ElasticsearchClient client,
                                         ArticleRepository articleRepository,
                                         MultimediaFullTextService multimediaFullTextService,
                                         MultimediaSemanticSearchService multimediaSemanticSearchService) {
        this.client = client;
        this.articleRepository = articleRepository;
        this.multimediaFullTextService = multimediaFullTextService;
        this.multimediaSemanticSearchService = multimediaSemanticSearchService;
    }

    /**
     * Runs multimedia hybrid (RRF) search and flattens {@code inner_hits.matched_media} into cards.
     * Blank {@code q} is rejected; an ImageBind or Elasticsearch failure becomes a
     * {@link DependencyException}.
     */
    public MultimediaSearchPage search(MultimediaFullTextQuery query) {
        requireQuery(query);
        float[] queryVector = multimediaSemanticSearchService.embedText(query.q());
        SearchRequest request = buildSearchRequest(query, queryVector);
        try {
            SearchResponse<Map> response = client.search(request, Map.class);
            List<MultimediaSearchHit> items = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                items.addAll(MultimediaHitMapper.cards(hit, articleRepository, HYBRID_INNER_HITS_NAMES));
            }
            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new MultimediaSearchPage(List.copyOf(items), total);
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(ELASTICSEARCH, e);
        }
    }

    /** Package-private so unit tests can assert the cookbook RRF DSL without a live cluster. */
    SearchRequest buildSearchRequest(MultimediaFullTextQuery query, float[] queryVector) {
        requireQuery(query);
        int size = SearchPagination.normalizeSize(query.size());
        int page = SearchPagination.normalizePage(query.page());
        int from = SearchPagination.from(page, size);
        int rankWindow = Math.max(RANK_WINDOW_MIN, from + size);
        int numCandidates = Math.max(NUM_CANDIDATES_FLOOR, NUM_CANDIDATES_FACTOR * (from + size));

        Query bm25Leg = multimediaFullTextService.buildQuery(query);
        List<Float> vector = toVectorList(queryVector);
        Query nestedKnn = multimediaSemanticSearchService.nestedKnnQuery(
                vector, numCandidates, query.mediaTypes(), MultimediaHitMapper.INNER_HITS_NAME_KNN);
        List<Query> parentFilters = multimediaSemanticSearchService.parentFilters(
                query.statuses(), query.section(), query.language(), query.publishedFrom(), query.publishedTo());
        Query knnLeg = Query.of(q -> q.bool(b -> {
            b.must(nestedKnn);
            if (!parentFilters.isEmpty()) {
                b.filter(parentFilters);
            }
            return b;
        }));

        SearchRequest request = new SearchRequest.Builder()
                .index(INDEX)
                .from(from)
                .size(size)
                .trackTotalHits(track -> track.enabled(true))
                .source(src -> src.filter(f -> f.includes(MultimediaHitMapper.PARENT_SOURCE_FIELDS)))
                .retriever(r -> r.rrf(rrf -> rrf
                        .retrievers(List.of(
                                RRFRetrieverEntry.of(e -> e.retriever(sr -> sr.standard(s -> s.query(bm25Leg)))),
                                RRFRetrieverEntry.of(e -> e.retriever(sr -> sr.standard(s -> s.query(knnLeg))))))
                        .rankWindowSize(rankWindow)
                        .rankConstant(RANK_CONSTANT)))
                .build();
        log.debug("Multimedia hybrid from={} size={} rankWindow={} numCandidates={}",
                from, size, rankWindow, numCandidates);
        return request;
    }

    private static void requireQuery(MultimediaFullTextQuery query) {
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
}
