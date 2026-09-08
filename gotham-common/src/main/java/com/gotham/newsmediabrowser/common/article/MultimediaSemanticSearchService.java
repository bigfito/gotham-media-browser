package com.gotham.newsmediabrowser.common.article;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.index.IndexDefinition;
import com.gotham.newsmediabrowser.common.media.MediaType;
import com.gotham.newsmediabrowser.common.search.SearchPagination;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Multimedia semantic search ({@code docs/elasticsearch-search-methods.md} §8): the query text is
 * embedded with ImageBind and matched against the nested {@code multimedia.asset_vector} dense
 * vector via a {@code knn} clause that carries {@code inner_hits.matched_media}, so the UI renders
 * the matched asset cards (public {@code storage_uri}) exactly as full-text does.
 *
 * <p>This same nested kNN backs the file-upload vector mode (§10, P8-T03); only the source of the
 * query vector differs (uploaded bytes instead of query text).
 */
@Service
public class MultimediaSemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(MultimediaSemanticSearchService.class);
    private static final String INDEX = IndexDefinition.MEDIA_BROWSER.indexName();
    private static final String ELASTICSEARCH = "Elasticsearch";
    private static final String IMAGEBIND = "ImageBind";
    private static final String VECTOR_FIELD = "multimedia.asset_vector";

    /** kNN candidate tunables (cookbook §11): {@code num_candidates = max(100, 4 * k)}. */
    private static final int NUM_CANDIDATES_FLOOR = 100;
    private static final int NUM_CANDIDATES_FACTOR = 4;

    private final ElasticsearchClient client;
    private final ArticleRepository articleRepository;
    private final ImageBindClient imageBindClient;

    public MultimediaSemanticSearchService(ElasticsearchClient client,
                                           ArticleRepository articleRepository,
                                           ImageBindClient imageBindClient) {
        this.client = client;
        this.articleRepository = articleRepository;
        this.imageBindClient = imageBindClient;
    }

    /**
     * Runs multimedia semantic (nested kNN) search over the query text. Blank {@code q} is rejected;
     * an ImageBind or Elasticsearch failure becomes a {@link DependencyException}.
     */
    public MultimediaSearchPage search(MultimediaFullTextQuery query) {
        requireQuery(query);
        float[] queryVector = embedText(query.q());
        return searchByVector(queryVector, query.statuses(), query.section(), query.language(),
                query.publishedFrom(), query.publishedTo(), query.mediaTypes(), query.page(), query.size());
    }

    /**
     * Nested kNN over an already-computed query vector. Shared entry point for semantic (text →
     * vector, this class) and the file-upload vector mode (§10, P8-T03).
     */
    public MultimediaSearchPage searchByVector(float[] queryVector,
                                               List<ArticleStatus> statuses,
                                               String section,
                                               String language,
                                               Instant publishedFrom,
                                               Instant publishedTo,
                                               List<MediaType> mediaTypes,
                                               int page,
                                               int size) {
        SearchRequest request = buildSearchRequest(queryVector, statuses, section, language,
                publishedFrom, publishedTo, mediaTypes, page, size);
        try {
            SearchResponse<Map> response = client.search(request, Map.class);
            List<MultimediaSearchHit> items = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                items.addAll(MultimediaHitMapper.cards(hit, articleRepository));
            }
            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new MultimediaSearchPage(List.copyOf(items), total);
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(ELASTICSEARCH, e);
        }
    }

    /** Embeds the query text and guards the {@value ImageBindClient#EMBEDDING_DIM}-d contract. */
    float[] embedText(String q) {
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

    /**
     * Package-private so unit tests can assert the cookbook nested-kNN DSL without a live cluster.
     *
     * <p>The vector lives on the nested {@code multimedia} path, so the kNN runs as a {@code knn}
     * <em>query</em> wrapped in a {@code nested} query that carries {@code inner_hits.matched_media}
     * (cookbook §8's "explicit nested knn context"). A top-level {@code knn} search option returns
     * the parent but leaves {@code inner_hits} empty for nested vectors, so the matched asset could
     * not be rendered — this nested form populates them exactly like the full-text path.
     */
    SearchRequest buildSearchRequest(float[] queryVector,
                                     List<ArticleStatus> statuses,
                                     String section,
                                     String language,
                                     Instant publishedFrom,
                                     Instant publishedTo,
                                     List<MediaType> mediaTypes,
                                     int requestedPage,
                                     int requestedSize) {
        requireEmbeddingDim(queryVector);
        int size = SearchPagination.normalizeSize(requestedSize);
        int page = SearchPagination.normalizePage(requestedPage);
        int from = SearchPagination.from(page, size);
        int numCandidates = Math.max(NUM_CANDIDATES_FLOOR, NUM_CANDIDATES_FACTOR * (from + size));
        List<Float> vector = toVectorList(queryVector);
        Query nestedKnn = nestedKnnQuery(vector, numCandidates, mediaTypes);
        List<Query> parentFilters = parentFilters(statuses, section, language, publishedFrom, publishedTo);

        SearchRequest request = new SearchRequest.Builder()
                .index(INDEX)
                .from(from)
                .size(size)
                .trackTotalHits(track -> track.enabled(true))
                .source(src -> src.filter(f -> f.includes(MultimediaHitMapper.PARENT_SOURCE_FIELDS)))
                .query(q -> q.bool(b -> {
                    b.must(nestedKnn);
                    if (!parentFilters.isEmpty()) {
                        b.filter(parentFilters);
                    }
                    return b;
                }))
                .build();
        log.debug("Multimedia semantic from={} size={} numCandidates={}", from, size, numCandidates);
        return request;
    }

    /**
     * Nested {@code knn} query on {@code multimedia.asset_vector} with {@code inner_hits.matched_media}.
     * When media types are selected they filter <em>inside</em> the nested context (per §8) so only
     * matching assets are considered and surfaced.
     */
    Query nestedKnnQuery(List<Float> vector, int numCandidates, List<MediaType> mediaTypes) {
        return nestedKnnQuery(vector, numCandidates, mediaTypes, MultimediaHitMapper.INNER_HITS_NAME);
    }

    /**
     * Same nested kNN query but with a caller-chosen {@code inner_hits} name. Hybrid search fuses
     * this leg with a BM25 leg under RRF, which rejects two legs that share an inner-hits name, so
     * the hybrid path passes a distinct name.
     */
    Query nestedKnnQuery(List<Float> vector, int numCandidates, List<MediaType> mediaTypes, String innerHitsName) {
        Query knn = Query.of(q -> q.knn(k -> k
                .field(VECTOR_FIELD)
                .queryVector(vector)
                .numCandidates(numCandidates)));

        Query nestedBody;
        if (mediaTypes == null || mediaTypes.isEmpty()) {
            nestedBody = knn;
        } else {
            List<FieldValue> values = mediaTypes.stream().map(type -> FieldValue.of(type.name())).toList();
            Query typeFilter = Query.of(q -> q.terms(t -> t
                    .field("multimedia.media_type")
                    .terms(tf -> tf.value(values))));
            nestedBody = Query.of(q -> q.bool(b -> b.must(knn).filter(typeFilter)));
        }

        return Query.of(q -> q.nested(n -> n
                .path("multimedia")
                .query(nestedBody)
                .innerHits(ih -> ih
                        .name(innerHitsName)
                        .size(MultimediaHitMapper.INNER_HITS_SIZE)
                        .source(src -> src.filter(f -> f.includes(MultimediaHitMapper.INNER_SOURCE_FIELDS))))));
    }

    /** Parent-level filters (status/section/language/published range) applied to the article document. */
    List<Query> parentFilters(List<ArticleStatus> statuses,
                              String section,
                              String language,
                              Instant publishedFrom,
                              Instant publishedTo) {
        List<Query> filters = new ArrayList<>();
        addStatusFilter(filters, statuses);
        addTermFilter(filters, "section", section);
        addTermFilter(filters, "language", language);
        addPublishedRange(filters, publishedFrom, publishedTo);
        return filters;
    }

    private static void requireQuery(MultimediaFullTextQuery query) {
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

    private static void addPublishedRange(List<Query> filters,
                                          Instant publishedFrom,
                                          Instant publishedTo) {
        if (publishedFrom == null && publishedTo == null) {
            return;
        }
        filters.add(Query.of(q -> q.range(r -> r.date(d -> {
            d.field("published_at");
            if (publishedFrom != null) {
                d.gte(publishedFrom.toString());
            }
            if (publishedTo != null) {
                d.lte(publishedTo.toString());
            }
            return d;
        }))));
    }
}
