package com.gotham.newsmediabrowser.common.article;

import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.time.Instant;
import java.util.List;

/**
 * Inputs for article {@code mode=fulltext} search ({@code docs/elasticsearch-search-methods.md} §4).
 *
 * @param q             required query string
 * @param fields        UI checkbox values (remapped to ES fields; empty → title/subtitle/summary/body)
 * @param statuses      optional {@code terms} filter; empty/null = any status
 * @param section       optional keyword filter
 * @param language      optional keyword filter
 * @param publishedFrom optional {@code published_at} range start (inclusive)
 * @param publishedTo   optional {@code published_at} range end (inclusive)
 * @param journalist    optional nested byline filter (ES id or free-text name)
 * @param sort          score or field sort
 * @param page          1-based UI page
 * @param size          requested page size (clamped to 25/50/100)
 */
public record ArticleFullTextQuery(
        String q,
        List<String> fields,
        List<ArticleStatus> statuses,
        String section,
        String language,
        Instant publishedFrom,
        Instant publishedTo,
        String journalist,
        SearchSort sort,
        int page,
        int size) {

    public ArticleFullTextQuery {
        fields = fields != null ? List.copyOf(fields) : List.of();
        statuses = statuses != null ? List.copyOf(statuses) : List.of();
        sort = sort != null ? sort : SearchSort.RELEVANCE;
    }

    /** Convenience constructor for tests: query text only, defaults for the rest. */
    public static ArticleFullTextQuery of(String q) {
        return new ArticleFullTextQuery(q, List.of(), List.of(), null, null, null, null, null,
                SearchSort.RELEVANCE, 1, 25);
    }
}
