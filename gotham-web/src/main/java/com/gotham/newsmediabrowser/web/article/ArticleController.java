package com.gotham.newsmediabrowser.web.article;

import com.gotham.newsmediabrowser.common.article.Article;
import com.gotham.newsmediabrowser.common.article.ArticleJournalist;
import com.gotham.newsmediabrowser.common.article.ArticlePage;
import com.gotham.newsmediabrowser.common.article.ArticleRepository;
import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.error.NotFoundException;
import com.gotham.newsmediabrowser.common.journalist.Journalist;
import com.gotham.newsmediabrowser.common.journalist.JournalistRepository;
import jakarta.validation.Valid;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Serves the {@code /article} CRUD screens. P4-T04 covers list + create/edit of text, metadata and
 * nested journalist bylines (no media upload yet — that arrives in P5); delete is P4-T05.
 */
@Controller
public class ArticleController {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(25, 50, 100);
    private static final int DEFAULT_SIZE = 25;
    /** Upper bound when loading journalists to offer as byline options (prototype scale). */
    private static final int MAX_JOURNALIST_OPTIONS = 1000;

    private final ArticleRepository articleRepository;
    private final JournalistRepository journalistRepository;

    public ArticleController(ArticleRepository articleRepository, JournalistRepository journalistRepository) {
        this.articleRepository = articleRepository;
        this.journalistRepository = journalistRepository;
    }

    @GetMapping("/article")
    public String list(
            @RequestParam(name = "status", required = false) String statusFilter,
            @RequestParam(name = "page", defaultValue = "1") int requestedPage,
            @RequestParam(name = "size", defaultValue = "25") int requestedSize,
            Model model) {

        int size = ALLOWED_SIZES.contains(requestedSize) ? requestedSize : DEFAULT_SIZE;
        int page = Math.max(requestedPage, 1);
        int from = (page - 1) * size;
        ArticleStatus status = parseStatusFilter(statusFilter);

        ArticlePage result = articleRepository.findAll(status, null, from, size);
        List<ArticleRow> rows = result.items().stream().map(this::toRow).toList();

        long total = result.total();
        int totalPages = (int) Math.max(1, Math.ceil((double) total / size));

        addChrome(model);
        model.addAttribute("rows", rows);
        model.addAttribute("total", total);
        model.addAttribute("size", size);
        model.addAttribute("allowedSizes", List.of(25, 50, 100));
        model.addAttribute("statuses", List.of(ArticleStatus.values()));
        model.addAttribute("statusFilter", status != null ? status.name() : "");
        model.addAttribute("page", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("from", from);
        model.addAttribute("showingFrom", rows.isEmpty() ? 0 : from + 1);
        model.addAttribute("showingTo", from + rows.size());
        model.addAttribute("hasPrev", page > 1);
        model.addAttribute("hasNext", page < totalPages);
        return "article/list";
    }

    @GetMapping("/article/new")
    public String newForm(Model model) {
        addFormChrome(model, "New article", "/article", null);
        model.addAttribute("articleForm", new ArticleForm());
        return "article/form";
    }

    @PostMapping("/article")
    public String create(
            @Valid @ModelAttribute("articleForm") ArticleForm articleForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        Map<String, Journalist> journalists = journalistsById();
        Optional<Article> built = buildOrReject(articleForm, bindingResult, journalists);
        if (built.isEmpty()) {
            addFormChrome(model, "New article", "/article", null);
            return "article/form";
        }

        Article created = articleRepository.create(built.get());
        redirectAttributes.addFlashAttribute("flash", "Created “" + created.title() + "”.");
        return "redirect:/article";
    }

    @GetMapping("/article/{id}")
    public String editForm(@PathVariable String id, Model model) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Article " + id + " was not found."));
        addFormChrome(model, "Edit article", "/article/" + id, id);
        model.addAttribute("articleForm", ArticleForm.fromArticle(article));
        return "article/form";
    }

    @PostMapping("/article/{id}")
    public String update(
            @PathVariable String id,
            @Valid @ModelAttribute("articleForm") ArticleForm articleForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        Article existing = articleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Article " + id + " was not found."));

        Map<String, Journalist> journalists = journalistsById();
        Optional<Article> built = buildOrReject(articleForm, bindingResult, journalists);
        if (built.isEmpty()) {
            addFormChrome(model, "Edit article", "/article/" + id, id);
            return "article/form";
        }

        Article toSave = built.get().withId(id).withTimestamps(existing.createdAt(), existing.updatedAt());
        Article saved = articleRepository.update(toSave);
        redirectAttributes.addFlashAttribute("flash", "Updated “" + saved.title() + "”.");
        return "redirect:/article";
    }

    /**
     * Deletes an article document. GCS cleanup for its media is added in P5-T03.
     *
     * @throws NotFoundException if no article has that id (rendered as the branded 404 page)
     */
    @PostMapping("/article/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes redirectAttributes) {
        if (!articleRepository.deleteById(id)) {
            throw new NotFoundException("Article " + id + " was not found.");
        }
        redirectAttributes.addFlashAttribute("flash", "Deleted the article.");
        return "redirect:/article";
    }

    /**
     * Builds the article from the form, or returns empty after registering validation errors:
     * field-binding errors, an unresolvable byline, or an unparseable published date.
     */
    private Optional<Article> buildOrReject(
            ArticleForm form, BindingResult bindingResult, Map<String, Journalist> journalists) {

        Function<String, Journalist> lookup = journalists::get;
        if (!bindingResult.hasFieldErrors("journalistIds") && !form.hasResolvableByline(lookup)) {
            bindingResult.rejectValue("journalistIds", "byline.unresolved",
                    "Select at least one existing journalist for the byline.");
        }
        if (bindingResult.hasErrors()) {
            return Optional.empty();
        }
        try {
            return Optional.of(form.toNewArticle(lookup));
        } catch (DateTimeParseException e) {
            bindingResult.rejectValue("publishedAt", "publishedAt.invalid",
                    "Enter a valid date and time.");
            return Optional.empty();
        }
    }

    private Map<String, Journalist> journalistsById() {
        return journalistRepository.findAll(0, MAX_JOURNALIST_OPTIONS).items().stream()
                .collect(Collectors.toMap(Journalist::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private ArticleStatus parseStatusFilter(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) {
            return null;
        }
        try {
            return ArticleStatus.fromValue(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ArticleRow toRow(Article article) {
        String bylines = article.journalists().stream()
                .map(ArticleJournalist::fullName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(" · "));
        return new ArticleRow(article.id(), article.title(), article.status(),
                article.metadata() != null ? article.metadata().section() : null, bylines);
    }

    private void addChrome(Model model) {
        model.addAttribute("activePage", "article");
        model.addAttribute("imagebindStatus", "checking");
        model.addAttribute("elasticsearchStatus", "checking");
    }

    private void addFormChrome(Model model, String heading, String formAction, String articleId) {
        addChrome(model);
        model.addAttribute("heading", heading);
        model.addAttribute("formAction", formAction);
        model.addAttribute("articleId", articleId);
        model.addAttribute("journalistOptions", journalistRepository.findAll(0, MAX_JOURNALIST_OPTIONS).items());
        model.addAttribute("roles", List.of("AUTHOR", "CO_AUTHOR", "CONTRIBUTING"));
    }

    /** One row of the article list, formatted for display. */
    public record ArticleRow(String id, String title, ArticleStatus status, String section, String bylines) {}
}
