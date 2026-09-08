package com.gotham.newsmediabrowser.datagen.orchestrator;

import java.util.List;

/**
 * Payload record representing a generated article ready to be submitted to {@code POST /article}.
 */
public record ArticlePayload(
        String title,
        String subtitle,
        String summary,
        String body,
        String status,
        String language,
        String section,
        String tags,
        String location,
        String source,
        String seoTitle,
        String seoDescription,
        String seoKeywords,
        String slug,
        String publishedAt,
        String canonicalUrl,
        List<String> journalistIds,
        String imagePrompt,
        String audioScript,
        String videoPrompt
) {
    public ArticlePayload {
        if (title == null || title.isBlank()) title = "Untitled Gotham News Story";
        if (subtitle == null || subtitle.isBlank()) subtitle = title;
        if (summary == null || summary.isBlank()) summary = title;
        if (body == null || body.isBlank()) body = summary;
        if (status == null || status.isBlank()) status = "PUBLISHED";
        if (language == null || language.isBlank()) language = "en";
        if (section == null || section.isBlank()) section = "Politics";
        if (tags == null || tags.isBlank()) tags = section.toLowerCase() + ",gotham";
        if (location == null || location.isBlank()) location = "Gotham City";
        if (source == null || source.isBlank()) source = "Gotham Gazette";
        if (seoTitle == null || seoTitle.isBlank()) seoTitle = title;
        if (seoDescription == null || seoDescription.isBlank()) seoDescription = summary;
        if (seoKeywords == null || seoKeywords.isBlank()) seoKeywords = tags;
        if (slug == null || slug.isBlank()) slug = slugify(title);
        if (publishedAt == null || publishedAt.isBlank()) publishedAt = "2026-09-01T09:00";
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
            canonicalUrl = "https://www.gothamgazette.example/articles/" + slug;
        }
        if (journalistIds == null) journalistIds = List.of();
    }

    static String slugify(String value) {
        String slug = value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        return slug.isBlank() ? "gotham-story" : slug;
    }
}
