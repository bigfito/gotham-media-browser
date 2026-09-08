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
        List<String> journalistIds,
        String imagePrompt,
        String audioScript,
        String videoPrompt
) {
    public ArticlePayload {
        if (title == null || title.isBlank()) title = "Untitled Gotham News Story";
        if (summary == null || summary.isBlank()) summary = title;
        if (body == null || body.isBlank()) body = summary;
        if (status == null || status.isBlank()) status = "PUBLISHED";
        if (language == null || language.isBlank()) language = "en";
        if (section == null || section.isBlank()) section = "Politics";
        if (tags == null) tags = "";
        if (location == null || location.isBlank()) location = "Gotham City";
        if (source == null || source.isBlank()) source = "Gotham Gazette";
        if (journalistIds == null) journalistIds = List.of();
    }
}
