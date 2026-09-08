package com.gotham.newsmediabrowser.common.search;

import java.util.Set;

/**
 * Maps UI {@code page}/{@code size} to Elasticsearch {@code from}/{@code size}. Allowed page sizes
 * are locked to {@code 25}, {@code 50}, and {@code 100}.
 */
public final class SearchPagination {

    public static final Set<Integer> ALLOWED_SIZES = Set.of(25, 50, 100);
    public static final int DEFAULT_SIZE = 25;

    private SearchPagination() {}

    /** Clamps a requested page to {@code >= 1}. */
    public static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /** Returns {@code size} when it is 25/50/100, otherwise the default of 25. */
    public static int normalizeSize(int size) {
        return ALLOWED_SIZES.contains(size) ? size : DEFAULT_SIZE;
    }

    /** Elasticsearch {@code from} offset for a 1-based UI page. */
    public static int from(int page, int size) {
        return (normalizePage(page) - 1) * normalizeSize(size);
    }
}
