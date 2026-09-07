package com.gotham.newsmediabrowser.common.article;

import java.util.Locale;

/**
 * Publication state of an article. Stored as a keyword ({@code status}) and used as a public search
 * filter. All three states are publicly searchable.
 */
public enum ArticleStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    /**
     * Parses a status from user/stored input, case-insensitively and trimmed.
     *
     * @throws IllegalArgumentException if the value is null/blank or not a known status
     */
    public static ArticleStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Article status is required (DRAFT, PUBLISHED or ARCHIVED).");
        }
        try {
            return ArticleStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown article status: '" + value + "'.", e);
        }
    }
}
