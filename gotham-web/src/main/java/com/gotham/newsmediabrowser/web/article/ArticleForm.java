package com.gotham.newsmediabrowser.web.article;

import com.gotham.newsmediabrowser.common.article.Article;
import com.gotham.newsmediabrowser.common.article.ArticleJournalist;
import com.gotham.newsmediabrowser.common.article.ArticleMetadata;
import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.article.ContributionRole;
import com.gotham.newsmediabrowser.common.journalist.Journalist;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Backing bean for the article create/edit forms (text + metadata + bylines; media is added in P5).
 *
 * <p>Mutable with getters/setters for Spring MVC binding and Thymeleaf re-rendering. Per-journalist
 * byline order and role are bound as maps keyed by the journalist id, so the checkbox list and its
 * inputs stay in sync.
 */
public class ArticleForm {

    @NotBlank(message = "Title is required.")
    @Size(max = 512, message = "Title must be at most 512 characters.")
    private String title;

    @Size(max = 512, message = "Subtitle must be at most 512 characters.")
    private String subtitle;

    @NotBlank(message = "Summary is required.")
    private String summary;

    @NotBlank(message = "Body is required.")
    private String body;

    @Size(max = 256, message = "Slug must be at most 256 characters.")
    private String slug;

    @NotBlank(message = "Status is required.")
    private String status = ArticleStatus.DRAFT.name();

    @Size(max = 16, message = "Language must be at most 16 characters.")
    private String language = "en";

    /** From a datetime-local input (e.g. {@code 2026-09-01T12:00}); interpreted as UTC. Optional. */
    private String publishedAt;

    // --- Metadata ---
    private String section;
    /** Comma-separated in the UI; split into keyword[] on save. */
    private String tags;
    private String location;
    private String source;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
    private String canonicalUrl;

    // --- Bylines ---
    @NotEmpty(message = "Select at least one journalist for the byline.")
    private List<String> journalistIds = new ArrayList<>();

    /** Byline order per journalist id (1-based in the UI). */
    private Map<String, Integer> bylineOrder = new LinkedHashMap<>();

    /** Contribution role per journalist id. */
    private Map<String, String> role = new LinkedHashMap<>();

    /**
     * Builds a not-yet-persisted article, snapshotting the selected journalists (resolved via the
     * given lookup) into nested bylines ordered by the form's byline order.
     *
     * @param journalistById resolves a selected id to its master {@link Journalist}, or {@code null}
     *     if it no longer exists (such ids are skipped)
     */
    public Article toNewArticle(Function<String, Journalist> journalistById) {
        List<ArticleJournalist> bylines = new ArrayList<>();
        for (String id : journalistIds) {
            Journalist journalist = journalistById.apply(id);
            if (journalist == null) {
                continue; // selected journalist was deleted between load and submit
            }
            int order = bylineOrder.getOrDefault(id, bylines.size() + 1);
            ContributionRole contributionRole = ContributionRole.fromValue(role.get(id));
            bylines.add(ArticleJournalist.fromJournalist(journalist, order, contributionRole));
        }

        return Article.newArticle(
                strip(title), strip(subtitle), strip(summary), strip(body), strip(slug),
                ArticleStatus.fromValue(status), strip(language), parsePublishedAt(),
                new ArticleMetadata(strip(section), splitTags(), strip(location), strip(source),
                        strip(seoTitle), strip(seoDescription), strip(seoKeywords), strip(canonicalUrl)),
                bylines);
    }

    /** True when at least one selected journalist actually resolves to a master record. */
    public boolean hasResolvableByline(Function<String, Journalist> journalistById) {
        return journalistIds.stream().map(journalistById).anyMatch(j -> j != null);
    }

    private Instant parsePublishedAt() {
        if (publishedAt == null || publishedAt.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(publishedAt.strip()).toInstant(ZoneOffset.UTC);
    }

    private List<String> splitTags() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::strip)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }

    private String strip(String value) {
        return value != null && !value.isBlank() ? value.strip() : null;
    }

    // --- Prefill from an existing article (for edit) ---

    /** Populates a form from an existing article (used by the edit screen). */
    public static ArticleForm fromArticle(Article article) {
        ArticleForm form = new ArticleForm();
        form.title = article.title();
        form.subtitle = article.subtitle();
        form.summary = article.summary();
        form.body = article.body();
        form.slug = article.slug();
        form.status = article.status() != null ? article.status().name() : ArticleStatus.DRAFT.name();
        form.language = article.language();
        form.publishedAt = article.publishedAt() != null
                ? LocalDateTime.ofInstant(article.publishedAt(), ZoneOffset.UTC).toString() : null;

        ArticleMetadata metadata = article.metadata() != null ? article.metadata() : ArticleMetadata.empty();
        form.section = metadata.section();
        form.tags = String.join(", ", metadata.tags());
        form.location = metadata.location();
        form.source = metadata.source();
        form.seoTitle = metadata.seoTitle();
        form.seoDescription = metadata.seoDescription();
        form.seoKeywords = metadata.seoKeywords();
        form.canonicalUrl = metadata.canonicalUrl();

        for (ArticleJournalist byline : article.journalists()) {
            form.journalistIds.add(byline.journalistId());
            form.bylineOrder.put(byline.journalistId(), byline.bylineOrder());
            form.role.put(byline.journalistId(),
                    byline.contributionRole() != null ? byline.contributionRole().name() : "");
        }
        return form;
    }

    // --- getters / setters ---

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSeoTitle() {
        return seoTitle;
    }

    public void setSeoTitle(String seoTitle) {
        this.seoTitle = seoTitle;
    }

    public String getSeoDescription() {
        return seoDescription;
    }

    public void setSeoDescription(String seoDescription) {
        this.seoDescription = seoDescription;
    }

    public String getSeoKeywords() {
        return seoKeywords;
    }

    public void setSeoKeywords(String seoKeywords) {
        this.seoKeywords = seoKeywords;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public void setCanonicalUrl(String canonicalUrl) {
        this.canonicalUrl = canonicalUrl;
    }

    public List<String> getJournalistIds() {
        return journalistIds;
    }

    public void setJournalistIds(List<String> journalistIds) {
        this.journalistIds = journalistIds != null ? journalistIds : new ArrayList<>();
    }

    public Map<String, Integer> getBylineOrder() {
        return bylineOrder;
    }

    public void setBylineOrder(Map<String, Integer> bylineOrder) {
        this.bylineOrder = bylineOrder != null ? bylineOrder : new LinkedHashMap<>();
    }

    public Map<String, String> getRole() {
        return role;
    }

    public void setRole(Map<String, String> role) {
        this.role = role != null ? role : new LinkedHashMap<>();
    }
}
