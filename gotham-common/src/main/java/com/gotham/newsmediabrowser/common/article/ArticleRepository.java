package com.gotham.newsmediabrowser.common.article;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.index.IndexDefinition;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes article documents in the {@code gotham-media-browser} index.
 *
 * <p>On every write the denormalized projection fields ({@code journalist_names} /
 * {@code journalist_bios}) are recomputed from the structured bylines via {@link ArticleProjections},
 * and the nested {@code journalists[]} snapshots are rebuilt — so the document never drifts from its
 * structured data. On write the {@code article_embedding} is computed from the article's text via
 * {@link ImageBindClient} (P6-T03); per-asset {@code asset_vector}s are computed upstream at upload
 * and carried on the nested multimedia. If the embedder is unavailable the write still succeeds
 * without the article vector (logged at WARN), so Elasticsearch stays the only hard CRUD dependency.
 * Writes refresh immediately for a consistent list view at this prototype's volume; any Elasticsearch
 * failure surfaces as a {@link DependencyException} (HTTP 503).
 */
@Repository
public class ArticleRepository {

    private static final Logger log = LoggerFactory.getLogger(ArticleRepository.class);
    private static final String INDEX = IndexDefinition.MEDIA_BROWSER.indexName();
    private static final String SERVICE = "Elasticsearch";
    /** Page size used when sweeping every article that nests a journalist (cascade-strip). */
    private static final int SWEEP_PAGE_SIZE = 500;
    /** Heavy vector fields excluded from list results (only needed for search + edit round-trip). */
    private static final List<String> VECTOR_FIELDS = List.of("article_embedding", "multimedia.asset_vector");

    private final ElasticsearchClient client;
    private final ImageBindClient imageBindClient;

    public ArticleRepository(ElasticsearchClient client, ImageBindClient imageBindClient) {
        this.client = client;
        this.imageBindClient = imageBindClient;
    }

    /** Creates a new article, stamping creation/update time. Elasticsearch generates the id. */
    public Article create(Article article) {
        Instant now = Instant.now();
        Article toStore = article.withId(null).withTimestamps(now, now);
        try {
            String id = client.index(request -> request
                    .index(INDEX)
                    .document(toDocument(toStore))
                    .refresh(Refresh.True)).id();
            log.info("Created article '{}' with id {}", toStore.title(), id);
            return toStore.withId(id);
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /** Finds an article by id, or empty if there is no such document. */
    public Optional<Article> findById(String id) {
        try {
            // includes("*") re-includes the dense_vectors that ES 9 serverless excludes from _source
            // by default, so an edit re-save preserves each asset_vector instead of wiping it.
            var response = client.get(request -> request.index(INDEX).id(id)
                    .sourceIncludes("*"), Map.class);
            if (!response.found() || response.source() == null) {
                return Optional.empty();
            }
            return Optional.of(fromSource(response.id(), asMap(response.source())));
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /**
     * Overwrites an existing article, refreshing the update time and preserving creation time.
     *
     * @throws IllegalArgumentException if the article has no id (nothing to update)
     */
    public Article update(Article article) {
        if (article.id() == null || article.id().isBlank()) {
            throw new IllegalArgumentException("Cannot update an article without an id.");
        }
        Instant createdAt = article.createdAt() != null ? article.createdAt() : Instant.now();
        Article toStore = article.withTimestamps(createdAt, Instant.now());
        try {
            client.index(request -> request
                    .index(INDEX)
                    .id(toStore.id())
                    .document(toDocument(toStore))
                    .refresh(Refresh.True));
            log.info("Updated article id {}", toStore.id());
            return toStore;
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /**
     * Deletes an article by id.
     *
     * @return {@code true} if a document was deleted, {@code false} if none existed
     */
    public boolean deleteById(String id) {
        try {
            var response = client.delete(request -> request.index(INDEX).id(id).refresh(Refresh.True));
            log.info("Delete article id {} -> {}", id, response.result().jsonValue());
            return "deleted".equals(response.result().jsonValue());
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /**
     * Lists articles newest first, optionally filtered by status and/or a bylined journalist.
     *
     * @param status       optional status filter ({@code null} = any)
     * @param journalistId optional nested journalist filter ({@code null} = any)
     */
    public ArticlePage findAll(ArticleStatus status, String journalistId, int from, int size) {
        Query query = listQuery(status, journalistId);
        try {
            SearchResponse<Map> response = client.search(request -> request
                    .index(INDEX)
                    .query(query)
                    .from(from)
                    .size(size)
                    .source(src -> src.filter(f -> f.excludes(VECTOR_FIELDS)))
                    .trackTotalHits(track -> track.enabled(true))
                    .sort(sort -> sort.field(field -> field.field("created_at").order(SortOrder.Desc))),
                    Map.class);

            List<Article> items = response.hits().hits().stream()
                    .map(hit -> fromSource(hit.id(), asMap(hit.source())))
                    .toList();
            long total = response.hits().total() != null ? response.hits().total().value() : items.size();
            return new ArticlePage(items, total);
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /**
     * Returns every article that nests the given journalist id, sweeping all pages. Used by the
     * cascade-strip delete (P4-T03) to find the articles whose bylines must be rebuilt.
     */
    public List<Article> findByJournalistId(String journalistId) {
        List<Article> all = new ArrayList<>();
        int from = 0;
        while (true) {
            ArticlePage page = findAll(null, journalistId, from, SWEEP_PAGE_SIZE);
            all.addAll(page.items());
            from += SWEEP_PAGE_SIZE;
            if (from >= page.total() || page.items().isEmpty()) {
                return all;
            }
        }
    }

    // --- Query building ---

    private Query listQuery(ArticleStatus status, String journalistId) {
        List<Query> filters = new ArrayList<>();
        if (status != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("status").value(status.name()))));
        }
        if (journalistId != null && !journalistId.isBlank()) {
            filters.add(byJournalistId(journalistId));
        }
        if (filters.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }
        return Query.of(q -> q.bool(b -> b.filter(filters)));
    }

    private Query byJournalistId(String journalistId) {
        return Query.of(q -> q.nested(n -> n
                .path("journalists")
                .query(nq -> nq.term(t -> t.field("journalists.journalist_id").value(journalistId)))));
    }

    // --- Article <-> Elasticsearch document mapping (package-private for direct unit tests) ---

    /** Builds the Elasticsearch {@code _source} for an article, recomputing projections + bylines. */
    Map<String, Object> toDocument(Article article) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("title", article.title());
        document.put("subtitle", article.subtitle());
        document.put("summary", article.summary());
        document.put("body", article.body());
        document.put("slug", article.slug());
        document.put("status", article.status() != null ? article.status().name() : null);
        document.put("language", article.language());
        document.put("published_at", toIso(article.publishedAt()));
        document.put("created_at", toIso(article.createdAt()));
        document.put("updated_at", toIso(article.updatedAt()));

        ArticleMetadata metadata = article.metadata() != null ? article.metadata() : ArticleMetadata.empty();
        document.put("section", metadata.section());
        document.put("tags", metadata.tags());
        document.put("location", metadata.location());
        document.put("source", metadata.source());
        document.put("seo_title", metadata.seoTitle());
        document.put("seo_description", metadata.seoDescription());
        document.put("seo_keywords", metadata.seoKeywords());
        document.put("canonical_url", metadata.canonicalUrl());

        // Denormalized projections, always rebuilt from the structured data.
        document.put("journalist_names", ArticleProjections.journalistNames(article.journalists()));
        document.put("journalist_bios", ArticleProjections.journalistBios(article.journalists()));
        document.put("multimedia_text", ArticleProjections.multimediaText(article.multimedia()));

        document.put("journalists", ArticleProjections.orderedByByline(article.journalists()).stream()
                .map(this::toNestedJournalist)
                .toList());
        document.put("multimedia", ArticleProjections.orderedByPosition(article.multimedia()).stream()
                .map(this::toNestedMultimedia)
                .toList());

        // Article-level embedding, recomputed on every write from the current text (P6-T03).
        List<Float> articleEmbedding = embedArticleText(article);
        if (articleEmbedding != null) {
            document.put("article_embedding", articleEmbedding);
        }
        return document;
    }

    /**
     * Embeds the article's text with ImageBind, or returns {@code null} (logged) if the embedder is
     * unavailable — the article still saves, just without its semantic vector.
     */
    private List<Float> embedArticleText(Article article) {
        String text = buildEmbeddingText(article);
        if (text.isBlank()) {
            return null;
        }
        try {
            return toFloatList(imageBindClient.embedText(text));
        } catch (RuntimeException e) {
            log.warn("Skipping article_embedding — ImageBind unavailable: {}", e.toString());
            return null;
        }
    }

    /** Composes the text that represents the article for semantic search. */
    private String buildEmbeddingText(Article article) {
        ArticleMetadata metadata = article.metadata() != null ? article.metadata() : ArticleMetadata.empty();
        return Stream.of(
                        article.title(), article.subtitle(), article.summary(), article.body(),
                        metadata.section(), String.join(" ", metadata.tags()))
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private List<Float> toFloatList(float[] vector) {
        if (vector == null) {
            return null;
        }
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }

    private Map<String, Object> toNestedMultimedia(ArticleMultimedia media) {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("multimedia_element_id", media.multimediaElementId());
        nested.put("media_type", media.mediaType() != null ? media.mediaType().name() : null);
        nested.put("storage_uri", media.storageUri());
        nested.put("mime_type", media.mimeType());
        nested.put("position", media.position());
        nested.put("caption", media.caption());
        nested.put("credit", media.credit());
        nested.put("title", media.title());
        nested.put("description", media.description());
        nested.put("alt_text", media.altText());
        nested.put("original_filename", media.originalFilename());
        nested.put("file_size_bytes", media.fileSizeBytes());
        nested.put("checksum", media.checksum());
        nested.put("width", media.width());
        nested.put("height", media.height());
        nested.put("duration_ms", media.durationMs());
        nested.put("codec", media.codec());
        nested.put("bitrate_kbps", media.bitrateKbps());
        nested.put("frame_rate", media.frameRate());
        nested.put("sample_rate_hz", media.sampleRateHz());
        nested.put("channels", media.channels());
        if (media.assetVector() != null && !media.assetVector().isEmpty()) {
            nested.put("asset_vector", media.assetVector());
        }
        return nested;
    }

    private Map<String, Object> toNestedJournalist(ArticleJournalist journalist) {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("journalist_id", journalist.journalistId());
        nested.put("first_name", journalist.firstName());
        nested.put("last_name", journalist.lastName());
        nested.put("full_name", journalist.fullName());
        nested.put("email", journalist.email());
        nested.put("bio", journalist.bio());
        nested.put("byline_order", journalist.bylineOrder());
        nested.put("contribution_role",
                journalist.contributionRole() != null ? journalist.contributionRole().name() : null);
        return nested;
    }

    /** Rebuilds an article from its id and Elasticsearch {@code _source}. */
    Article fromSource(String id, Map<String, Object> source) {
        ArticleMetadata metadata = new ArticleMetadata(
                asString(source.get("section")),
                asStringList(source.get("tags")),
                asString(source.get("location")),
                asString(source.get("source")),
                asString(source.get("seo_title")),
                asString(source.get("seo_description")),
                asString(source.get("seo_keywords")),
                asString(source.get("canonical_url")));

        List<ArticleJournalist> journalists = asMapList(source.get("journalists")).stream()
                .map(this::fromNestedJournalist)
                .toList();
        List<ArticleMultimedia> multimedia = asMapList(source.get("multimedia")).stream()
                .map(this::fromNestedMultimedia)
                .toList();

        return new Article(
                id,
                asString(source.get("title")),
                asString(source.get("subtitle")),
                asString(source.get("summary")),
                asString(source.get("body")),
                asString(source.get("slug")),
                source.get("status") != null ? ArticleStatus.fromValue(asString(source.get("status"))) : null,
                asString(source.get("language")),
                parseInstant(source.get("published_at")),
                parseInstant(source.get("created_at")),
                parseInstant(source.get("updated_at")),
                metadata,
                journalists,
                multimedia);
    }

    private ArticleMultimedia fromNestedMultimedia(Map<String, Object> nested) {
        return new ArticleMultimedia(
                asString(nested.get("multimedia_element_id")),
                nested.get("media_type") != null
                        ? com.gotham.newsmediabrowser.common.media.MediaType.valueOf(asString(nested.get("media_type")))
                        : null,
                asString(nested.get("storage_uri")),
                asString(nested.get("mime_type")),
                asInt(nested.get("position")),
                asString(nested.get("caption")),
                asString(nested.get("credit")),
                asString(nested.get("title")),
                asString(nested.get("description")),
                asString(nested.get("alt_text")),
                asString(nested.get("original_filename")),
                asLong(nested.get("file_size_bytes")),
                asString(nested.get("checksum")),
                asInteger(nested.get("width")),
                asInteger(nested.get("height")),
                asLong(nested.get("duration_ms")),
                asString(nested.get("codec")),
                asInteger(nested.get("bitrate_kbps")),
                asDouble(nested.get("frame_rate")),
                asInteger(nested.get("sample_rate_hz")),
                asInteger(nested.get("channels")),
                asFloatList(nested.get("asset_vector")));
    }

    private ArticleJournalist fromNestedJournalist(Map<String, Object> nested) {
        return new ArticleJournalist(
                asString(nested.get("journalist_id")),
                asString(nested.get("first_name")),
                asString(nested.get("last_name")),
                asString(nested.get("email")),
                asString(nested.get("bio")),
                asInt(nested.get("byline_order")),
                ContributionRole.fromValue(asString(nested.get("contribution_role"))));
    }

    private String toIso(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private Instant parseInstant(Object value) {
        return value != null ? Instant.parse(value.toString()) : null;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    /** Reads a JSON number array back into a {@code List<Float>} (for {@code asset_vector}). */
    private List<Float> asFloatList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Float> floats = new ArrayList<>(list.size());
        for (Object element : list) {
            floats.add(element instanceof Number number ? number.floatValue() : 0f);
        }
        return floats;
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        return value instanceof List<?> list ? list.stream().map(Object::toString).toList() : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
