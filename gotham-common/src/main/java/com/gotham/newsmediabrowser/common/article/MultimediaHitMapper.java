package com.gotham.newsmediabrowser.common.article;

import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.InnerHitsResult;
import co.elastic.clients.json.JsonData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattens a parent article hit and its {@code inner_hits.matched_media} nested hits into
 * {@link MultimediaSearchHit} cards.
 *
 * <p>Shared by every multimedia search mode ({@code docs/elasticsearch-search-methods.md} §7
 * full-text and §8/§10 nested kNN): they all attach the same {@code inner_hits} block and render
 * the same asset cards, so the mapping — and the {@code _source} field lists that shape it — live
 * here in one place instead of being copied per service.
 */
final class MultimediaHitMapper {

    /** Inner-hits block name; the parsed response is looked up by this key. */
    static final String INNER_HITS_NAME = "matched_media";

    /** Matched assets returned per parent article (cookbook §11 recommends 3–5). */
    static final int INNER_HITS_SIZE = 5;

    /** Parent {@code _source} kept on the top-level hit — enough to link back and label the card. */
    static final List<String> PARENT_SOURCE_FIELDS =
            List.of("title", "status", "section", "slug", "published_at");

    /** Nested {@code _source} kept on each matched asset — enough to render and play it. */
    static final List<String> INNER_SOURCE_FIELDS = List.of(
            "multimedia.multimedia_element_id",
            "multimedia.media_type",
            "multimedia.storage_uri",
            "multimedia.title",
            "multimedia.caption",
            "multimedia.description",
            "multimedia.alt_text",
            "multimedia.credit",
            "multimedia.mime_type",
            "multimedia.position");

    private MultimediaHitMapper() {}

    /**
     * Builds one card per matched nested asset. Returns an empty list when the parent hit carried no
     * {@code matched_media} inner hits (it matched only on parent projection fields, which have no
     * asset to show).
     *
     * @param hit        a parent article search hit
     * @param repository reused to rebuild each nested {@link ArticleMultimedia} from its source map
     */
    static List<MultimediaSearchHit> cards(Hit<Map> hit, ArticleRepository repository) {
        Map<String, Object> parent = asMap(hit.source());
        String articleId = hit.id();
        String title = asString(parent.get("title"));
        ArticleStatus status = parent.get("status") != null
                ? ArticleStatus.fromValue(asString(parent.get("status")))
                : null;
        String section = asString(parent.get("section"));
        String slug = asString(parent.get("slug"));
        Instant publishedAt = parseInstant(parent.get("published_at"));

        InnerHitsResult inner = hit.innerHits() != null ? hit.innerHits().get(INNER_HITS_NAME) : null;
        if (inner == null || inner.hits() == null || inner.hits().hits().isEmpty()) {
            return List.of();
        }
        List<MultimediaSearchHit> cards = new ArrayList<>();
        for (Hit<JsonData> innerHit : inner.hits().hits()) {
            Map<String, Object> nested = nestedSource(innerHit.source());
            ArticleMultimedia media = repository.fromNestedMultimedia(nested);
            cards.add(new MultimediaSearchHit(articleId, title, status, section, slug, publishedAt, media));
        }
        return cards;
    }

    /**
     * Unwraps a nested inner-hit {@code _source}. Elasticsearch returns it wrapped under the nested
     * path ({@code {"multimedia": {...}}}); this returns the inner asset map either way.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> nestedSource(JsonData source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> map = asMap(source.to(Object.class));
        Object nested = map.get("multimedia");
        if (nested instanceof Map<?, ?> wrapped) {
            return asMap(wrapped);
        }
        return map;
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, val) -> copy.put(String.valueOf(key), val));
            return copy;
        }
        return new LinkedHashMap<>();
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Instant parseInstant(Object value) {
        return value != null ? Instant.parse(value.toString()) : null;
    }
}
