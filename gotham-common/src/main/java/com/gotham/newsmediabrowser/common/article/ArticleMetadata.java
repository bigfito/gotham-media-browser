package com.gotham.newsmediabrowser.common.article;

import java.util.List;

/**
 * The 1:1 metadata that accompanies an article (its {@code ARTICLE_METADATA} row in the logical
 * model), flattened onto the article document.
 *
 * @param section        editorial section (keyword; also full-text searchable via {@code section.text})
 * @param tags           free-form tags (keyword array)
 * @param location       place the story relates to
 * @param source         attribution/source
 * @param seoTitle       SEO title
 * @param seoDescription SEO description
 * @param seoKeywords    SEO keywords
 * @param canonicalUrl   canonical URL (stored, not indexed)
 */
public record ArticleMetadata(
        String section,
        List<String> tags,
        String location,
        String source,
        String seoTitle,
        String seoDescription,
        String seoKeywords,
        String canonicalUrl) {

    /** An empty metadata block (no section, no tags, etc.) for articles created without metadata. */
    public static ArticleMetadata empty() {
        return new ArticleMetadata(null, List.of(), null, null, null, null, null, null);
    }

    /** Never-null tag list, for callers that iterate without null checks. */
    public List<String> tags() {
        return tags != null ? tags : List.of();
    }
}
