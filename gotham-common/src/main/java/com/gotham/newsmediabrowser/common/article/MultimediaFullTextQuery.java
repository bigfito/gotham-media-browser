package com.gotham.newsmediabrowser.common.article;

import com.gotham.newsmediabrowser.common.media.MediaType;
import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.time.Instant;
import java.util.List;

/**
 * Inputs for multimedia {@code mode=fulltext} search ({@code docs/elasticsearch-search-methods.md} §7).
 *
 * @param q             required query string
 * @param fields        UI checkbox values ({@code multimedia.*} nested + optional parent projections)
 * @param statuses      optional parent {@code terms} filter
 * @param section       optional parent keyword filter
 * @param language      optional parent keyword filter
 * @param publishedFrom optional {@code published_at} range start
 * @param publishedTo   optional {@code published_at} range end
 * @param mediaTypes    optional nested {@code multimedia.media_type} filter
 * @param sort          score or field sort on the parent article
 * @param page          1-based UI page (parent {@code from}/{@code size})
 * @param size          requested page size (clamped to 25/50/100)
 */
public record MultimediaFullTextQuery(
        String q,
        List<String> fields,
        List<ArticleStatus> statuses,
        String section,
        String language,
        Instant publishedFrom,
        Instant publishedTo,
        List<MediaType> mediaTypes,
        SearchSort sort,
        int page,
        int size) {

    public MultimediaFullTextQuery {
        fields = fields != null ? List.copyOf(fields) : List.of();
        statuses = statuses != null ? List.copyOf(statuses) : List.of();
        mediaTypes = mediaTypes != null ? List.copyOf(mediaTypes) : List.of();
        sort = sort != null ? sort : SearchSort.RELEVANCE;
    }

    public static MultimediaFullTextQuery of(String q) {
        return new MultimediaFullTextQuery(q, List.of(), List.of(), null, null, null, null, List.of(),
                SearchSort.RELEVANCE, 1, 25);
    }
}
