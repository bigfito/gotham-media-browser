package com.gotham.newsmediabrowser.web.journalist;

import com.gotham.newsmediabrowser.common.error.NotFoundException;
import com.gotham.newsmediabrowser.common.journalist.Journalist;
import com.gotham.newsmediabrowser.common.journalist.JournalistPage;
import com.gotham.newsmediabrowser.common.journalist.JournalistRepository;
import com.gotham.newsmediabrowser.common.journalist.JournalistService;
import jakarta.validation.Valid;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
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
 * Serves the journalist master-data pages: the paginated list, read-only view, create (P3-T03),
 * and edit + cascade-strip delete (P4-T03). Editing/deleting ripple into article bylines via
 * {@link JournalistService}.
 */
@Controller
public class JournalistController {

    /** Page sizes the UI offers; anything else falls back to {@link #DEFAULT_SIZE}. */
    private static final Set<Integer> ALLOWED_SIZES = Set.of(25, 50, 100);
    private static final int DEFAULT_SIZE = 25;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);

    private final JournalistRepository journalistRepository;
    private final JournalistService journalistService;

    public JournalistController(JournalistRepository journalistRepository, JournalistService journalistService) {
        this.journalistRepository = journalistRepository;
        this.journalistService = journalistService;
    }

    @GetMapping("/journalist")
    public String list(
            @RequestParam(name = "page", defaultValue = "1") int requestedPage,
            @RequestParam(name = "size", defaultValue = "25") int requestedSize,
            Model model) {

        int size = ALLOWED_SIZES.contains(requestedSize) ? requestedSize : DEFAULT_SIZE;
        int page = Math.max(requestedPage, 1);
        int from = (page - 1) * size;

        JournalistPage result = journalistRepository.findAll(from, size);
        List<JournalistRow> rows = result.items().stream().map(this::toRow).toList();

        long total = result.total();
        int totalPages = (int) Math.max(1, Math.ceil((double) total / size));
        int showingFrom = rows.isEmpty() ? 0 : from + 1;
        int showingTo = from + rows.size();

        model.addAttribute("activePage", "journalist");
        model.addAttribute("imagebindStatus", "checking");
        model.addAttribute("elasticsearchStatus", "checking");

        model.addAttribute("rows", rows);
        model.addAttribute("total", total);
        model.addAttribute("size", size);
        model.addAttribute("allowedSizes", List.of(25, 50, 100));
        model.addAttribute("page", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("from", from);
        model.addAttribute("showingFrom", showingFrom);
        model.addAttribute("showingTo", showingTo);
        model.addAttribute("hasPrev", page > 1);
        model.addAttribute("hasNext", page < totalPages);

        return "journalist/list";
    }

    /** Renders the empty create form. */
    @GetMapping("/journalist/new")
    public String newForm(Model model) {
        addFormChrome(model);
        model.addAttribute("journalistForm", new JournalistForm());
        return "journalist/new";
    }

    /**
     * Creates a journalist. On validation failure the form is redisplayed with in-form errors; on
     * success it follows the Post/Redirect/Get pattern back to the list (so a refresh won't re-post).
     */
    @PostMapping("/journalist")
    public String create(
            @Valid @ModelAttribute("journalistForm") JournalistForm journalistForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormChrome(model);
            return "journalist/new";
        }

        Journalist created = journalistRepository.create(journalistForm.toNewJournalist());
        redirectAttributes.addFlashAttribute("flash", "Created " + created.fullName() + ".");
        return "redirect:/journalist";
    }

    /** Read-only page with the full journalist record. */
    @GetMapping("/journalist/{id}/view")
    public String view(@PathVariable String id, Model model) {
        Journalist journalist = journalistRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Journalist " + id + " was not found."));
        addFormChrome(model);
        model.addAttribute("journalist", journalist);
        addRecordChrome(model, journalist);
        return "journalist/view";
    }

    /** Renders the edit form for an existing journalist. */
    @GetMapping("/journalist/{id}")
    public String editForm(@PathVariable String id, Model model) {
        Journalist journalist = journalistRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Journalist " + id + " was not found."));
        addFormChrome(model);
        model.addAttribute("journalistForm", toForm(journalist));
        addRecordChrome(model, journalist);
        return "journalist/edit";
    }

    /**
     * Saves edits to a journalist and cascades the change onto every article that bylines it. On
     * validation failure the form is redisplayed with in-form errors; on success it redirects back to
     * the list (Post/Redirect/Get).
     */
    @PostMapping("/journalist/{id}")
    public String update(
            @PathVariable String id,
            @Valid @ModelAttribute("journalistForm") JournalistForm journalistForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        Journalist existing = journalistRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Journalist " + id + " was not found."));

        if (bindingResult.hasErrors()) {
            addFormChrome(model);
            addRecordChrome(model, existing);
            return "journalist/edit";
        }

        Journalist toSave = journalistForm.toNewJournalist()
                .withId(id)
                .withTimestamps(existing.createdAt(), existing.updatedAt());
        Journalist saved = journalistService.update(toSave);
        redirectAttributes.addFlashAttribute("flash", "Updated " + saved.fullName() + ".");
        return "redirect:/journalist";
    }

    /** Cascade-strips a journalist from all article bylines, then deletes the master document. */
    @PostMapping("/journalist/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes redirectAttributes) {
        journalistService.cascadeDelete(id);
        redirectAttributes.addFlashAttribute("flash", "Deleted journalist and stripped their bylines.");
        return "redirect:/journalist";
    }

    private JournalistForm toForm(Journalist journalist) {
        JournalistForm form = new JournalistForm();
        form.setFirstName(journalist.firstName());
        form.setLastName(journalist.lastName());
        form.setEmail(journalist.email());
        form.setBio(journalist.bio());
        return form;
    }

    /** Read-only record fields shown on the edit form (id + timestamps). */
    private void addRecordChrome(Model model, Journalist journalist) {
        model.addAttribute("journalistId", journalist.id());
        model.addAttribute("createdAt", journalist.createdAt() != null ? journalist.createdAt().toString() : "—");
        model.addAttribute("updatedAt", journalist.updatedAt() != null ? journalist.updatedAt().toString() : "—");
    }

    private void addFormChrome(Model model) {
        model.addAttribute("activePage", "journalist");
        model.addAttribute("imagebindStatus", "checking");
        model.addAttribute("elasticsearchStatus", "checking");
    }

    private JournalistRow toRow(Journalist journalist) {
        String updated = journalist.updatedAt() != null ? DATE_FORMAT.format(journalist.updatedAt()) : "—";
        return new JournalistRow(journalist.id(), journalist.fullName(), journalist.email(), updated);
    }

    /** A single table row, already formatted for display. */
    public record JournalistRow(String id, String fullName, String email, String updated) {}
}
