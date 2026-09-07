package com.gotham.newsmediabrowser.common.journalist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotham.newsmediabrowser.common.article.Article;
import com.gotham.newsmediabrowser.common.article.ArticleJournalist;
import com.gotham.newsmediabrowser.common.article.ArticleMetadata;
import com.gotham.newsmediabrowser.common.article.ArticleRepository;
import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.article.ContributionRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the cascade behaviour of editing and deleting a journalist. */
@ExtendWith(MockitoExtension.class)
class JournalistServiceTest {

    @Mock
    private JournalistRepository journalistRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Captor
    private ArgumentCaptor<Article> articleCaptor;

    private Article articleWith(List<ArticleJournalist> bylines) {
        return new Article("art1", "T", null, null, null, "t", ArticleStatus.PUBLISHED, "en",
                null, null, null, ArticleMetadata.empty(), bylines);
    }

    @Test
    void updateRefreshesMatchingBylinePreservingOrderAndRole() {
        Journalist updated = new Journalist("j_lois", "Louise", "Lane-Kent", "louise@gotham.news",
                "New bio", null, null);
        // Article bylines a stale copy of j_lois (order 1, CO_AUTHOR) plus another journalist.
        ArticleJournalist staleLois = new ArticleJournalist("j_lois", "Lois", "Lane", "old@x", "old bio",
                1, ContributionRole.CO_AUTHOR);
        ArticleJournalist other = new ArticleJournalist("j_clark", "Clark", "Kent", "clark@x", "bio",
                0, ContributionRole.AUTHOR);

        when(journalistRepository.update(updated)).thenReturn(updated);
        when(articleRepository.findByJournalistId("j_lois")).thenReturn(List.of(articleWith(List.of(staleLois, other))));

        service().update(updated);

        verify(articleRepository).update(articleCaptor.capture());
        List<ArticleJournalist> saved = articleCaptor.getValue().journalists();
        ArticleJournalist refreshed = saved.stream().filter(b -> b.journalistId().equals("j_lois")).findFirst().orElseThrow();
        assertThat(refreshed.firstName()).isEqualTo("Louise");
        assertThat(refreshed.lastName()).isEqualTo("Lane-Kent");
        assertThat(refreshed.fullName()).isEqualTo("Louise Lane-Kent");
        // Article-specific fields are preserved.
        assertThat(refreshed.bylineOrder()).isEqualTo(1);
        assertThat(refreshed.contributionRole()).isEqualTo(ContributionRole.CO_AUTHOR);
        // The other journalist is untouched.
        assertThat(saved).anyMatch(b -> b.journalistId().equals("j_clark") && b.firstName().equals("Clark"));
    }

    @Test
    void cascadeDeleteStripsBylineThenDeletesMasterInOrder() {
        ArticleJournalist lois = new ArticleJournalist("j_lois", "Lois", "Lane", "x", "bio", 0, ContributionRole.AUTHOR);
        ArticleJournalist clark = new ArticleJournalist("j_clark", "Clark", "Kent", "x", "bio", 1, ContributionRole.CO_AUTHOR);
        when(articleRepository.findByJournalistId("j_lois")).thenReturn(List.of(articleWith(List.of(lois, clark))));

        service().cascadeDelete("j_lois");

        verify(articleRepository).update(articleCaptor.capture());
        List<ArticleJournalist> remaining = articleCaptor.getValue().journalists();
        assertThat(remaining).extracting(ArticleJournalist::journalistId).containsExactly("j_clark");

        // Articles are reindexed before the master is removed (safe to retry on failure).
        InOrder inOrder = inOrder(articleRepository, journalistRepository);
        inOrder.verify(articleRepository).update(any(Article.class));
        inOrder.verify(journalistRepository).deleteById("j_lois");
    }

    @Test
    void cascadeDeleteWithNoAffectedArticlesStillDeletesMaster() {
        when(articleRepository.findByJournalistId("j_ghost")).thenReturn(List.of());

        service().cascadeDelete("j_ghost");

        verify(journalistRepository).deleteById("j_ghost");
    }

    private JournalistService service() {
        return new JournalistService(journalistRepository, articleRepository);
    }
}
