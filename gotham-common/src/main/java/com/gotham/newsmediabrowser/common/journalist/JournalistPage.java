package com.gotham.newsmediabrowser.common.journalist;

import java.util.List;

/**
 * One page of journalists plus the total number that match, so the UI can render pagination.
 *
 * @param items    the journalists on this page (already ordered)
 * @param total    total number of journalists in the index (across all pages)
 */
public record JournalistPage(List<Journalist> items, long total) {}
