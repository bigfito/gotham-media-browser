package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.gotham.newsmediabrowser.common.journalist.Journalist;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for byline snapshots and the derived projection fields. */
class ArticleProjectionsTest {

    private final Journalist lois = new Journalist("j_lois", "Lois", "Lane", "lois@gotham.news", "Ace reporter", null, null);
    private final Journalist clark = new Journalist("j_clark", "Clark", "Kent", "clark@gotham.news", "  ", null, null);

    @Test
    void fromJournalistSnapshotsFieldsAndDerivesFullName() {
        ArticleJournalist byline = ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR);

        assertThat(byline.journalistId()).isEqualTo("j_lois");
        assertThat(byline.firstName()).isEqualTo("Lois");
        assertThat(byline.lastName()).isEqualTo("Lane");
        assertThat(byline.email()).isEqualTo("lois@gotham.news");
        assertThat(byline.bio()).isEqualTo("Ace reporter");
        assertThat(byline.bylineOrder()).isZero();
        assertThat(byline.contributionRole()).isEqualTo(ContributionRole.AUTHOR);
        assertThat(byline.fullName()).isEqualTo("Lois Lane");
    }

    @Test
    void journalistNamesFollowBylineOrder() {
        // Provided out of order; projection must sort by bylineOrder.
        List<ArticleJournalist> bylines = List.of(
                ArticleJournalist.fromJournalist(clark, 1, ContributionRole.CO_AUTHOR),
                ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR));

        assertThat(ArticleProjections.journalistNames(bylines)).containsExactly("Lois Lane", "Clark Kent");
    }

    @Test
    void journalistBiosSkipBlankOnesButKeepOrder() {
        List<ArticleJournalist> bylines = List.of(
                ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR),
                ArticleJournalist.fromJournalist(clark, 1, null)); // clark's bio is blank

        assertThat(ArticleProjections.journalistBios(bylines)).containsExactly("Ace reporter");
    }

    @Test
    void projectionsAreEmptyForNoBylines() {
        assertThat(ArticleProjections.journalistNames(null)).isEmpty();
        assertThat(ArticleProjections.journalistBios(List.of())).isEmpty();
        assertThat(ArticleProjections.orderedByByline(null)).isEmpty();
    }
}
