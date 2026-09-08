package com.gotham.newsmediabrowser.common.article;

import java.time.Instant;
import java.util.List;

/**
 * One multimedia result card: the matched nested asset plus enough parent article fields to link
 * back to {@code /article/{id}} and play {@code storage_uri}.
 */
public record MultimediaSearchHit(
        String articleId,
        String articleTitle,
        ArticleStatus articleStatus,
        String section,
        String slug,
        Instant publishedAt,
        ArticleMultimedia media) {}
