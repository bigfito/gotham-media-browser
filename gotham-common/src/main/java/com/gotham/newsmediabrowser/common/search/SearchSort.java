package com.gotham.newsmediabrowser.common.search;

import java.util.Locale;

/**
 * UI {@code sort} values for public search. Field sorts apply to full-text; relevance omits ES
 * {@code sort} so score order is used.
 */
public enum SearchSort {
    RELEVANCE("relevance"),
    PUBLISHED_AT_DESC("published_at_desc"),
    PUBLISHED_AT_ASC("published_at_asc"),
    TITLE_ASC("title_asc");

    private final String param;

    SearchSort(String param) {
        this.param = param;
    }

    /** Query-string value ({@code published_at_desc}, …). */
    public String param() {
        return param;
    }

    /**
     * Parses a UI sort param. Blank or unknown values become {@link #RELEVANCE}.
     */
    public static SearchSort fromValue(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        for (SearchSort sort : values()) {
            if (sort.param.equals(normalized)) {
                return sort;
            }
        }
        return RELEVANCE;
    }
}
