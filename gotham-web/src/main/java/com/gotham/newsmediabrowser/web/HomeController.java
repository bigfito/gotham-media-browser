package com.gotham.newsmediabrowser.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing page: dual search panels (articles / multimedia) posting query params
 * to {@code /results}. Search execution is P7-T02+; health legends are probed
 * client-side from {@code chrome.js}.
 */
@Controller
public class HomeController {

    private static final String STATUS_CHECKING = "checking";

    @GetMapping("/")
    public String landing(Model model) {
        model.addAttribute("activePage", "search");
        model.addAttribute("imagebindStatus", STATUS_CHECKING);
        model.addAttribute("elasticsearchStatus", STATUS_CHECKING);
        return "index";
    }
}
