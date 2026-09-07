package com.gotham.newsmediabrowser.common.article;

import java.util.Comparator;
import java.util.List;

/**
 * Builds the denormalized projection fields the article document carries for search, derived from
 * its structured bylines.
 *
 * <p>These feed the {@code journalist_names} / {@code journalist_bios} fields, which the mapping in
 * turn {@code copy_to}es into {@code journalist_search_text}. Keeping the derivation here (instead of
 * in the model) means the projections are recomputed on every write and never drift. The multimedia
 * projection ({@code multimedia_text}) is added with multimedia support in P5.
 *
 * <p>Stateless utility — not instantiable.
 */
public final class ArticleProjections {

    private ArticleProjections() {
    }

    /** The bylines ordered by their byline position (stable for equal orders). */
    public static List<ArticleJournalist> orderedByByline(List<ArticleJournalist> journalists) {
        return safe(journalists).stream()
                .sorted(Comparator.comparingInt(ArticleJournalist::bylineOrder))
                .toList();
    }

    /** Full names of all bylined journalists, in byline order, skipping blanks. */
    public static List<String> journalistNames(List<ArticleJournalist> journalists) {
        return orderedByByline(journalists).stream()
                .map(ArticleJournalist::fullName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    /** Biographies of all bylined journalists, in byline order, skipping blanks. */
    public static List<String> journalistBios(List<ArticleJournalist> journalists) {
        return orderedByByline(journalists).stream()
                .map(ArticleJournalist::bio)
                .filter(bio -> bio != null && !bio.isBlank())
                .map(String::strip)
                .toList();
    }

    /** Multimedia assets ordered by their position (stable for equal positions). */
    public static List<ArticleMultimedia> orderedByPosition(List<ArticleMultimedia> multimedia) {
        return safeMedia(multimedia).stream()
                .sorted(Comparator.comparingInt(ArticleMultimedia::position))
                .toList();
    }

    /**
     * The searchable text of all multimedia assets (title/caption/credit/description/alt text), in
     * position order. Feeds {@code multimedia_text}, which the mapping copies into
     * {@code multimedia_search_text}.
     */
    public static List<String> multimediaText(List<ArticleMultimedia> multimedia) {
        return orderedByPosition(multimedia).stream()
                .flatMap(ArticleMultimedia::searchableText)
                .toList();
    }

    private static List<ArticleJournalist> safe(List<ArticleJournalist> journalists) {
        return journalists != null ? journalists : List.of();
    }

    private static List<ArticleMultimedia> safeMedia(List<ArticleMultimedia> multimedia) {
        return multimedia != null ? multimedia : List.of();
    }
}
