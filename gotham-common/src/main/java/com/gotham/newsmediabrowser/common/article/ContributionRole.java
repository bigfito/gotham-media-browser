package com.gotham.newsmediabrowser.common.article;

import java.util.Locale;

/**
 * How a journalist contributed to an article's byline. Optional per authorship: a byline may carry
 * no role, so {@link #fromValue(String)} returns {@code null} for empty input.
 */
public enum ContributionRole {
    AUTHOR,
    CO_AUTHOR,
    CONTRIBUTING;

    /**
     * Parses a role case-insensitively, or {@code null} when none is given (the role is optional).
     *
     * @throws IllegalArgumentException if a non-blank value is not a known role
     */
    public static ContributionRole fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ContributionRole.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown contribution role: '" + value + "'.", e);
        }
    }
}
