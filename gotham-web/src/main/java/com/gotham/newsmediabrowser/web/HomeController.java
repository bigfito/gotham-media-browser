package com.gotham.newsmediabrowser.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing page controller.
 *
 * <p>P0 renders the shared chrome (header legends + MIT footer) around a placeholder
 * body. The dual search panels are implemented in P7-T01; the live health legends are
 * wired in P1-T03. Until then the legends render in the neutral {@code checking} state.
 */
@Controller
public class HomeController {

    private static final String STATUS_CHECKING = "checking";

    @GetMapping("/")
    public String landing(Model model) {
        model.addAttribute("activePage", "search");
        // Placeholder until P1-T03 injects real ImageBind / Elasticsearch health.
        model.addAttribute("imagebindStatus", STATUS_CHECKING);
        model.addAttribute("elasticsearchStatus", STATUS_CHECKING);
        return "index";
    }
}
