package com.gotham.newsmediabrowser.common.article;

import java.util.List;

/**
 * One page of articles plus the total number that match the query, so the UI can paginate.
 *
 * @param items the articles on this page (already ordered)
 * @param total total matching articles across all pages
 */
public record ArticlePage(List<Article> items, long total) {}
