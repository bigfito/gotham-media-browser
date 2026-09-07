package com.gotham.newsmediabrowser.common.article;

import java.time.Instant;
import java.util.List;

/**
 * An article (one document in the {@code gotham-media-browser} index), with its 1:1 metadata and its
 * nested journalist bylines. Multimedia is added in P5; this model covers text + metadata + bylines.
 *
 * <p>Immutable. {@code id} is the Elasticsearch {@code _id} (auto-generated, {@code null} until the
 * article is created). Denormalized projection fields ({@code journalist_names}, {@code
 * journalist_bios}, {@code multimedia_text}) are not stored here — they are derived at write time by
 * {@link ArticleProjections} so they cannot drift from the structured data.
 */
public record Article(
        String id,
        String title,
        String subtitle,
        String summary,
        String body,
        String slug,
        ArticleStatus status,
        String language,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        ArticleMetadata metadata,
        List<ArticleJournalist> journalists) {

    /**
     * Creates a not-yet-persisted article (no id, no timestamps). The repository assigns those on
     * create.
     */
    public static Article newArticle(
            String title,
            String subtitle,
            String summary,
            String body,
            String slug,
            ArticleStatus status,
            String language,
            Instant publishedAt,
            ArticleMetadata metadata,
            List<ArticleJournalist> journalists) {
        return new Article(null, title, subtitle, summary, body, slug, status, language, publishedAt,
                null, null, metadata != null ? metadata : ArticleMetadata.empty(), safe(journalists));
    }

    /** Never-null journalist list, for callers that iterate without null checks. */
    public List<ArticleJournalist> journalists() {
        return journalists != null ? journalists : List.of();
    }

    /** Returns a copy with the given Elasticsearch id. */
    public Article withId(String newId) {
        return new Article(newId, title, subtitle, summary, body, slug, status, language, publishedAt,
                createdAt, updatedAt, metadata, journalists);
    }

    /** Returns a copy with the given creation/update timestamps. */
    public Article withTimestamps(Instant created, Instant updated) {
        return new Article(id, title, subtitle, summary, body, slug, status, language, publishedAt,
                created, updated, metadata, journalists);
    }

    /** Returns a copy with the given journalist bylines (e.g. after a cascade reindex). */
    public Article withJournalists(List<ArticleJournalist> newJournalists) {
        return new Article(id, title, subtitle, summary, body, slug, status, language, publishedAt,
                createdAt, updatedAt, metadata, safe(newJournalists));
    }

    private static List<ArticleJournalist> safe(List<ArticleJournalist> journalists) {
        return journalists != null ? List.copyOf(journalists) : List.of();
    }
}
