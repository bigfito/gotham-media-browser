package com.gotham.newsmediabrowser.common.article;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the Article aggregate: factory defaults and immutable copies. */
class ArticleTest {

    @Test
    void newArticleHasNoIdOrTimestampsAndNeverNullCollections() {
        Article article = Article.newArticle("Bat-signal returns", null, "summary", "body",
                "bat-signal-returns", ArticleStatus.DRAFT, "en", null, null, null);

        assertThat(article.id()).isNull();
        assertThat(article.createdAt()).isNull();
        assertThat(article.updatedAt()).isNull();
        assertThat(article.journalists()).isEmpty();
        assertThat(article.metadata()).isEqualTo(ArticleMetadata.empty());
    }

    @Test
    void withIdAndTimestampsReturnCopiesLeavingOriginalUnchanged() {
        Article original = Article.newArticle("T", null, null, null, "t", ArticleStatus.PUBLISHED,
                "en", null, ArticleMetadata.empty(), List.of());

        Instant now = Instant.parse("2026-05-05T05:05:05Z");
        Article stored = original.withId("art1").withTimestamps(now, now);

        assertThat(stored.id()).isEqualTo("art1");
        assertThat(stored.createdAt()).isEqualTo(now);
        assertThat(original.id()).isNull();
        assertThat(original.createdAt()).isNull();
    }

    @Test
    void metadataTagsAreNeverNull() {
        ArticleMetadata metadata = new ArticleMetadata("City", null, "Gotham", "Wire",
                null, null, null, null);
        assertThat(metadata.tags()).isEmpty();
    }
}
