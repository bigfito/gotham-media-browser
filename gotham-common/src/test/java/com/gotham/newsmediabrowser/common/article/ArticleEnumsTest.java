package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Unit tests for the article enums and their lenient parsing. */
class ArticleEnumsTest {

    @Test
    void articleStatusParsesCaseInsensitively() {
        assertThat(ArticleStatus.fromValue("draft")).isEqualTo(ArticleStatus.DRAFT);
        assertThat(ArticleStatus.fromValue(" Published ")).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(ArticleStatus.fromValue("ARCHIVED")).isEqualTo(ArticleStatus.ARCHIVED);
    }

    @Test
    void articleStatusRejectsBlankAndUnknown() {
        assertThatThrownBy(() -> ArticleStatus.fromValue(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArticleStatus.fromValue("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArticleStatus.fromValue("retired"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retired");
    }

    @Test
    void contributionRoleIsOptionalAndCaseInsensitive() {
        assertThat(ContributionRole.fromValue(null)).isNull();
        assertThat(ContributionRole.fromValue("")).isNull();
        assertThat(ContributionRole.fromValue("author")).isEqualTo(ContributionRole.AUTHOR);
        assertThat(ContributionRole.fromValue("co_author")).isEqualTo(ContributionRole.CO_AUTHOR);
    }

    @Test
    void contributionRoleRejectsUnknownNonBlank() {
        assertThatThrownBy(() -> ContributionRole.fromValue("editor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("editor");
    }
}
