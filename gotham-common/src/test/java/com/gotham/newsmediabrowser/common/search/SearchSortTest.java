package com.gotham.newsmediabrowser.common.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchSortTest {

    @Test
    void parsesUiParamsAndDefaultsUnknownToRelevance() {
        assertThat(SearchSort.fromValue("published_at_desc")).isEqualTo(SearchSort.PUBLISHED_AT_DESC);
        assertThat(SearchSort.fromValue("TITLE_ASC")).isEqualTo(SearchSort.TITLE_ASC);
        assertThat(SearchSort.fromValue(null)).isEqualTo(SearchSort.RELEVANCE);
        assertThat(SearchSort.fromValue("nope")).isEqualTo(SearchSort.RELEVANCE);
    }
}
