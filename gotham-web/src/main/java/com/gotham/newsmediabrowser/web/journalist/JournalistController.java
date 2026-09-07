package com.gotham.newsmediabrowser.web.journalist;

import com.gotham.newsmediabrowser.common.journalist.Journalist;
import com.gotham.newsmediabrowser.common.journalist.JournalistPage;
import com.gotham.newsmediabrowser.common.journalist.JournalistRepository;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the journalist master-data pages. This task (P3-T02) implements the paginated list at
 * {@code GET /journalist}; create/edit/delete are wired in P3-T03 and P4-T03.
 */
@Controller
public class JournalistController {

    /** Page sizes the UI offers; anything else falls back to {@link #DEFAULT_SIZE}. */
    private static final Set<Integer> ALLOWED_SIZES = Set.of(25, 50, 100);
    private static final int DEFAULT_SIZE = 25;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);

    private final JournalistRepository journalistRepository;

    public JournalistController(JournalistRepository journalistRepository) {
        this.journalistRepository = journalistRepository;
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

    private JournalistRow toRow(Journalist journalist) {
        String updated = journalist.updatedAt() != null ? DATE_FORMAT.format(journalist.updatedAt()) : "—";
        return new JournalistRow(journalist.id(), journalist.fullName(), journalist.email(), updated);
    }

    /** A single table row, already formatted for display. */
    public record JournalistRow(String id, String fullName, String email, String updated) {}
}
