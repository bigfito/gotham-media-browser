package com.gotham.newsmediabrowser.common.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps landing-page full-text checkbox values to analyzable Elasticsearch fields
 * ({@code docs/elasticsearch-search-methods.md} §3).
 */
public final class FullTextFieldRemap {

    /** Article defaults when {@code fields} is empty. */
    public static final List<String> ARTICLE_DEFAULTS = List.of("title", "subtitle", "summary", "body");

    /** Multimedia nested defaults (used by P7-T03). */
    public static final List<String> MULTIMEDIA_NESTED_DEFAULTS =
            List.of("multimedia.title", "multimedia.caption", "multimedia.description", "multimedia.alt_text");

    private static final Map<String, String> ARTICLE_FIELDS;

    static {
        Map<String, String> article = new LinkedHashMap<>();
        article.put("title", "title");
        article.put("subtitle", "subtitle");
        article.put("summary", "summary");
        article.put("body", "body");
        article.put("section", "section.text");
        article.put("tags", "tags.text");
        article.put("location", "location.text");
        article.put("source", "source.text");
        article.put("seo_title", "seo_title");
        article.put("seo_description", "seo_description");
        article.put("seo_keywords", "seo_keywords");
        article.put("journalist_names", "journalist_names");
        article.put("journalist_bios", "journalist_bios");
        article.put("journalist_search_text", "journalist_search_text");
        article.put("article_search_text", "article_search_text");
        article.put("multimedia_text", "multimedia_text");
        article.put("multimedia_search_text", "multimedia_search_text");
        ARTICLE_FIELDS = Map.copyOf(article);
    }

    private FullTextFieldRemap() {}

    /**
     * Remaps UI article field names to ES {@code multi_match} fields. Unknown or nested
     * {@code multimedia.*} checkboxes are ignored (those need a nested query). If nothing usable
     * remains, the article defaults are used.
     */
    public static List<String> articleFields(Collection<String> uiFields) {
        if (uiFields == null || uiFields.isEmpty()) {
            return ARTICLE_DEFAULTS;
        }
        Set<String> mapped = new LinkedHashSet<>();
        for (String uiField : uiFields) {
            if (uiField == null || uiField.isBlank()) {
                continue;
            }
            String esField = ARTICLE_FIELDS.get(uiField.strip());
            if (esField != null) {
                mapped.add(esField);
            }
        }
        return mapped.isEmpty() ? ARTICLE_DEFAULTS : List.copyOf(mapped);
    }

    /** Article-level UI checkbox names that remap (excludes nested {@code multimedia.*}). */
    public static Set<String> articleUiFields() {
        return ARTICLE_FIELDS.keySet();
    }

    static List<String> knownArticleEsFields() {
        return new ArrayList<>(ARTICLE_FIELDS.values());
    }
}
