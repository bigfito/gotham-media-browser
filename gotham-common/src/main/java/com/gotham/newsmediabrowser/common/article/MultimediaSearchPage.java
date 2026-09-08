package com.gotham.newsmediabrowser.common.article;

import java.util.List;

/**
 * Flattened multimedia cards for the current parent page, plus the parent-hit total used for
 * {@code from}/{@code size} pagination ({@code docs/elasticsearch-search-methods.md} §7).
 *
 * @param items cards (one per {@code inner_hits.matched_media} hit)
 * @param total matching parent articles ({@code track_total_hits})
 */
public record MultimediaSearchPage(List<MultimediaSearchHit> items, long total) {}
