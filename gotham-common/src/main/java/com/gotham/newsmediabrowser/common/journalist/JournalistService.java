package com.gotham.newsmediabrowser.common.journalist;

import com.gotham.newsmediabrowser.common.article.Article;
import com.gotham.newsmediabrowser.common.article.ArticleJournalist;
import com.gotham.newsmediabrowser.common.article.ArticleRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Journalist operations that ripple into the denormalized article documents.
 *
 * <p>Because bylines are snapshotted onto articles, editing or deleting a journalist must also fix
 * every article that nests it. Elasticsearch has no multi-document transaction, so these run as a
 * best-effort sweep: the article reindexing happens first and the master change is finished last, so
 * a mid-sweep failure (surfaced as a {@code DependencyException} / 503) leaves the operation safe to
 * retry rather than leaving a deleted journalist with stale bylines still referencing it.
 */
@Service
public class JournalistService {

    private static final Logger log = LoggerFactory.getLogger(JournalistService.class);

    private final JournalistRepository journalistRepository;
    private final ArticleRepository articleRepository;

    public JournalistService(JournalistRepository journalistRepository, ArticleRepository articleRepository) {
        this.journalistRepository = journalistRepository;
        this.articleRepository = articleRepository;
    }

    /**
     * Updates the master journalist and refreshes its snapshot on every article that bylines it,
     * preserving each byline's order and role.
     */
    public Journalist update(Journalist journalist) {
        List<Article> affected = articleRepository.findByJournalistId(journalist.id());
        for (Article article : affected) {
            List<ArticleJournalist> refreshed = article.journalists().stream()
                    .map(byline -> journalist.id().equals(byline.journalistId())
                            ? ArticleJournalist.fromJournalist(
                                    journalist, byline.bylineOrder(), byline.contributionRole())
                            : byline)
                    .toList();
            articleRepository.update(article.withJournalists(refreshed));
        }
        Journalist saved = journalistRepository.update(journalist);
        log.info("Updated journalist {} and refreshed bylines on {} article(s)", saved.id(), affected.size());
        return saved;
    }

    /**
     * Cascade-strips a journalist: removes its nested byline from every article that references it
     * (rebuilding those articles' projections on reindex), then deletes the master document.
     *
     * <p>An article left with no bylines after stripping is kept as-is; re-bylining it is an editorial
     * action, not a side effect of this delete.
     */
    public void cascadeDelete(String journalistId) {
        List<Article> affected = articleRepository.findByJournalistId(journalistId);
        for (Article article : affected) {
            List<ArticleJournalist> remaining = article.journalists().stream()
                    .filter(byline -> !journalistId.equals(byline.journalistId()))
                    .toList();
            articleRepository.update(article.withJournalists(remaining));
        }
        boolean deleted = journalistRepository.deleteById(journalistId);
        log.info("Cascade-deleted journalist {} (master removed: {}) from {} article(s)",
                journalistId, deleted, affected.size());
    }
}
