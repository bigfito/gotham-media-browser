package com.gotham.newsmediabrowser.common.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearchPaginationTest {

    @ParameterizedTest
    @ValueSource(ints = {25, 50, 100})
    void allowedSizesPassThrough(int size) {
        assertThat(SearchPagination.normalizeSize(size)).isEqualTo(size);
    }

    @Test
    void invalidSizeFallsBackTo25() {
        assertThat(SearchPagination.normalizeSize(7)).isEqualTo(25);
        assertThat(SearchPagination.normalizeSize(0)).isEqualTo(25);
    }

    @Test
    void pageAndFromFollowUiContract() {
        assertThat(SearchPagination.normalizePage(0)).isEqualTo(1);
        assertThat(SearchPagination.from(1, 25)).isEqualTo(0);
        assertThat(SearchPagination.from(2, 50)).isEqualTo(50);
        assertThat(SearchPagination.from(3, 100)).isEqualTo(200);
    }
}
