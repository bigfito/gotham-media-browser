package com.gotham.newsmediabrowser.web.search;

import com.gotham.newsmediabrowser.common.article.Article;
import com.gotham.newsmediabrowser.common.article.ArticleFullTextQuery;
import com.gotham.newsmediabrowser.common.article.ArticleFullTextService;
import com.gotham.newsmediabrowser.common.article.ArticleJournalist;
import com.gotham.newsmediabrowser.common.article.ArticlePage;
import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.article.MultimediaFullTextQuery;
import com.gotham.newsmediabrowser.common.article.MultimediaFullTextService;
import com.gotham.newsmediabrowser.common.article.MultimediaSearchHit;
import com.gotham.newsmediabrowser.common.article.MultimediaSearchPage;
import com.gotham.newsmediabrowser.common.error.BadRequestException;
import com.gotham.newsmediabrowser.common.media.MediaType;
import com.gotham.newsmediabrowser.common.search.SearchPagination;
import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Public {@code /results} pages: article and multimedia FTS (P7-T02/T03) with filters, sort, and
 * pagination that round-trip the IA query params. Semantic / hybrid / vector ranking is P8.
 */
@Controller
public class ResultsController {

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US).withZone(ZoneOffset.UTC);
    private static final List<String> ARTICLE_FIELD_OPTIONS = List.of(
            "title", "subtitle", "summary", "body", "section", "tags", "location", "source",
            "seo_title", "seo_description", "seo_keywords", "journalist_names", "journalist_bios",
            "journalist_search_text", "article_search_text");
    private static final List<String> ARTICLE_FIELD_DEFAULTS = List.of("title", "subtitle", "summary", "body");
    private static final List<String> MEDIA_FIELD_OPTIONS = List.of(
            "multimedia.title", "multimedia.caption", "multimedia.description", "multimedia.alt_text",
            "multimedia.credit", "multimedia_text", "multimedia_search_text");
    private static final List<String> MEDIA_FIELD_DEFAULTS = List.of(
            "multimedia.title", "multimedia.caption", "multimedia.description", "multimedia.alt_text");
    private static final List<String> SECTION_OPTIONS = List.of("Politics", "Business", "Culture");
    private static final List<String> LANGUAGE_OPTIONS = List.of("en", "es");
    private static final String MODE_NOTICE =
            "Semantic, hybrid, and vector ranking ship in a later phase. Full-text search is available now.";

    private final ArticleFullTextService articleFullTextService;
    private final MultimediaFullTextService multimediaFullTextService;

    public ResultsController(ArticleFullTextService articleFullTextService,
                             MultimediaFullTextService multimediaFullTextService) {
        this.articleFullTextService = articleFullTextService;
        this.multimediaFullTextService = multimediaFullTextService;
    }

    @GetMapping("/results")
    public String resultsGet(
            @RequestParam(name = "entity", defaultValue = "article") String entity,
            @RequestParam(name = "mode", defaultValue = "fulltext") String mode,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "fields", required = false) List<String> fields,
            @RequestParam(name = "status", required = false) List<String> status,
            @RequestParam(name = "journalist", required = false) String journalist,
            @RequestParam(name = "section", required = false) String section,
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "mediaType", required = false) List<String> mediaType,
            @RequestParam(name = "published_from", required = false) String publishedFrom,
            @RequestParam(name = "published_to", required = false) String publishedTo,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "25") int size,
            Model model) {
        return render(entity, mode, q, fields, status, journalist, section, language, mediaType,
                publishedFrom, publishedTo, sort, page, size, model);
    }

    /** Vector mode on the landing page posts multipart; FTS still uses GET. */
    @PostMapping("/results")
    public String resultsPost(
            @RequestParam(name = "entity", defaultValue = "multimedia") String entity,
            @RequestParam(name = "mode", defaultValue = "fulltext") String mode,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "fields", required = false) List<String> fields,
            @RequestParam(name = "status", required = false) List<String> status,
            @RequestParam(name = "journalist", required = false) String journalist,
            @RequestParam(name = "section", required = false) String section,
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "mediaType", required = false) List<String> mediaType,
            @RequestParam(name = "published_from", required = false) String publishedFrom,
            @RequestParam(name = "published_to", required = false) String publishedTo,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "25") int size,
            Model model) {
        return render(entity, mode, q, fields, status, journalist, section, language, mediaType,
                publishedFrom, publishedTo, sort, page, size, model);
    }

    private String render(
            String entityRaw,
            String modeRaw,
            String q,
            List<String> fields,
            List<String> status,
            String journalist,
            String section,
            String language,
            List<String> mediaType,
            String publishedFrom,
            String publishedTo,
            String sortRaw,
            int requestedPage,
            int requestedSize,
            Model model) {

        String entity = normalizeEntity(entityRaw);
        String mode = normalizeMode(modeRaw);
        if ("article".equals(entity) && "vector".equals(mode)) {
            throw new BadRequestException("Vector search is only available for multimedia.");
        }

        int size = SearchPagination.normalizeSize(requestedSize);
        int page = SearchPagination.normalizePage(requestedPage);
        int from = SearchPagination.from(page, size);
        SearchSort sort = SearchSort.fromValue(sortRaw);
        List<String> selectedFields = fields != null ? fields : List.of();
        List<ArticleStatus> statuses = parseStatuses(status);
        List<String> statusValues = statuses.stream().map(ArticleStatus::name).toList();
        List<MediaType> mediaTypes = parseMediaTypes(mediaType);
        List<String> mediaTypeValues = mediaTypes.stream().map(MediaType::name).toList();
        boolean statusFilterActive = status != null && !status.isEmpty();
        boolean mediaTypeFilterActive = mediaType != null && !mediaType.isEmpty();
        List<String> uiFields = selectedFields.isEmpty()
                ? ("article".equals(entity) ? ARTICLE_FIELD_DEFAULTS : MEDIA_FIELD_DEFAULTS)
                : selectedFields;
        List<String> uiStatuses = statusFilterActive ? statusValues : List.of("PUBLISHED", "DRAFT", "ARCHIVED");
        List<String> uiMediaTypes = mediaTypeFilterActive
                ? mediaTypeValues
                : List.of("IMAGE", "AUDIO", "VIDEO");

        addChrome(model, entity);
        model.addAttribute("entity", entity);
        model.addAttribute("mode", mode);
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("fields", uiFields);
        model.addAttribute("fieldOptions", "article".equals(entity) ? ARTICLE_FIELD_OPTIONS : MEDIA_FIELD_OPTIONS);
        model.addAttribute("statusValues", uiStatuses);
        model.addAttribute("mediaTypeValues", uiMediaTypes);
        model.addAttribute("journalist", journalist != null ? journalist : "");
        model.addAttribute("section", section != null ? section : "");
        model.addAttribute("language", language != null ? language : "");
        model.addAttribute("publishedFrom", publishedFrom != null ? publishedFrom : "");
        model.addAttribute("publishedTo", publishedTo != null ? publishedTo : "");
        model.addAttribute("sort", sort.param());
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("from", from);
        model.addAttribute("allowedSizes", List.of(25, 50, 100));
        model.addAttribute("sectionOptions", SECTION_OPTIONS);
        model.addAttribute("languageOptions", LANGUAGE_OPTIONS);

        boolean fulltext = "fulltext".equals(mode);
        boolean hasQuery = q != null && !q.isBlank();
        if (!fulltext) {
            model.addAttribute("modeNotice", MODE_NOTICE);
        } else if (!hasQuery) {
            model.addAttribute("queryNotice", "Enter a search query.");
        }

        long total = 0;
        List<ArticleResultRow> articleRows = List.of();
        List<MultimediaSearchHit> mediaHits = List.of();

        if (fulltext && hasQuery) {
            Instant publishedStart = parseStart(publishedFrom);
            Instant publishedEnd = parseEnd(publishedTo);
            if ("article".equals(entity)) {
                ArticlePage result = articleFullTextService.search(new ArticleFullTextQuery(
                        q, selectedFields, statuses, blankToNull(section), blankToNull(language),
                        publishedStart, publishedEnd, blankToNull(journalist), sort, page, size));
                total = result.total();
                articleRows = result.items().stream().map(this::toRow).toList();
            } else {
                MultimediaSearchPage result = multimediaFullTextService.search(new MultimediaFullTextQuery(
                        q, selectedFields, statuses, blankToNull(section), blankToNull(language),
                        publishedStart, publishedEnd, mediaTypes, sort, page, size));
                total = result.total();
                mediaHits = result.items();
            }
        }

        int totalPages = (int) Math.max(1, Math.ceil((double) Math.max(total, 1) / size));
        if (total == 0) {
            totalPages = 1;
        }
        int shown = "article".equals(entity) ? articleRows.size() : mediaHits.size();
        int showingFrom = shown == 0 ? 0 : from + 1;
        int showingTo = shown == 0 ? 0 : from + shown;

        model.addAttribute("total", total);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrev", page > 1 && total > 0);
        model.addAttribute("hasNext", page < totalPages && total > 0);
        model.addAttribute("showingFrom", showingFrom);
        model.addAttribute("showingTo", showingTo);
        model.addAttribute("articleRows", articleRows);
        model.addAttribute("mediaHits", mediaHits);

        return "article".equals(entity) ? "results/articles" : "results/multimedia";
    }

    private ArticleResultRow toRow(Article article) {
        String bylines = article.journalists().stream()
                .map(ArticleJournalist::fullName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(" · "));
        String published = article.publishedAt() != null ? DISPLAY_DATE.format(article.publishedAt()) : "";
        String section = article.metadata() != null ? article.metadata().section() : null;
        return new ArticleResultRow(
                article.id(),
                article.title(),
                article.summary(),
                article.status(),
                section,
                bylines,
                published);
    }

    private static void addChrome(Model model, String entity) {
        model.addAttribute("activePage", "article".equals(entity) ? "results-articles" : "results-multimedia");
        model.addAttribute("imagebindStatus", "checking");
        model.addAttribute("elasticsearchStatus", "checking");
    }

    private static String normalizeEntity(String entity) {
        if (entity == null || entity.isBlank() || "article".equalsIgnoreCase(entity)) {
            return "article";
        }
        if ("multimedia".equalsIgnoreCase(entity) || "media".equalsIgnoreCase(entity)) {
            return "multimedia";
        }
        throw new BadRequestException("Search entity must be article or multimedia.");
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "fulltext";
        }
        String normalized = mode.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "fulltext", "semantic", "hybrid", "vector" -> normalized;
            default -> "fulltext";
        };
    }

    private static List<ArticleStatus> parseStatuses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ArticleStatus> parsed = new ArrayList<>();
        for (String value : raw) {
            try {
                parsed.add(ArticleStatus.fromValue(value));
            } catch (IllegalArgumentException ignored) {
                // skip unknown tokens
            }
        }
        return parsed;
    }

    private static List<MediaType> parseMediaTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<MediaType> parsed = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                parsed.add(MediaType.valueOf(value.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // skip unknown tokens
            }
        }
        return parsed;
    }

    private static Instant parseStart(String date) {
        LocalDate local = parseDate(date);
        return local != null ? local.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }

    private static Instant parseEnd(String date) {
        LocalDate local = parseDate(date);
        return local != null ? local.atTime(LocalTime.of(23, 59, 59)).toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record ArticleResultRow(
            String id,
            String title,
            String summary,
            ArticleStatus status,
            String section,
            String bylines,
            String published) {}
}
