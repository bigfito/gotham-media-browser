package com.gotham.newsmediabrowser.web;

import com.gotham.newsmediabrowser.web.journalist.JournalistOptions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing page: dual search panels (articles / multimedia) posting query params
 * to {@code /results}. Full-text search is executed there; health legends are probed
 * client-side from {@code chrome.js} against {@code /api/health/*}.
 *
 * <p>The article panel's journalist filter is a dropdown, so the roster is loaded here and handed to
 * the view as {@code journalistOptions}.
 */
@Controller
public class HomeController {

    private static final String STATUS_CHECKING = "checking";

    private final JournalistOptions journalistOptions;

    public HomeController(JournalistOptions journalistOptions) {
        this.journalistOptions = journalistOptions;
    }

    @GetMapping("/")
    public String landing(Model model) {
        model.addAttribute("activePage", "search");
        model.addAttribute("journalistOptions", journalistOptions.all());
        model.addAttribute("imagebindStatus", STATUS_CHECKING);
        model.addAttribute("elasticsearchStatus", STATUS_CHECKING);
        return "index";
    }
}
