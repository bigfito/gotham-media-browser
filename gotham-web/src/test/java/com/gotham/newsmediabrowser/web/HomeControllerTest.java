package com.gotham.newsmediabrowser.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.gotham.newsmediabrowser.web.journalist.JournalistOptions;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice tests for the P7-T01 landing: chrome plus dual search panels that
 * target {@code /results} with IA query params.
 */
@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JournalistOptions journalistOptions;

    @BeforeEach
    void rosterIsLoaded() {
        when(journalistOptions.all()).thenReturn(List.of(
                new JournalistOptions.Option("j_lois", "Lois Lane"),
                new JournalistOptions.Option("j_clark", "Clark Kent")));
    }

    @Test
    void journalistFilterIsADropdownOfTheRoster() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                // A select of ids, not a free-text box: the id filters by exact nested term.
                .andExpect(content().string(containsString("<select id=\"journalist\" name=\"journalist\">")))
                .andExpect(content().string(containsString("Any journalist")))
                .andExpect(content().string(containsString("value=\"j_lois\"")))
                .andExpect(content().string(containsString("Lois Lane")))
                .andExpect(content().string(not(containsString("placeholder=\"Name or id\""))));
    }

    @Test
    void landingRendersChromeAndReturns200() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("activePage", "search"))
                .andExpect(content().string(containsString("Gotham News")))
                .andExpect(content().string(containsString("ImageBind")))
                .andExpect(content().string(containsString("Elasticsearch")))
                .andExpect(content().string(containsString("MIT License")));
    }

    @Test
    void articlePanelPostsGetToResultsWithIaParams() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/results\"")))
                .andExpect(content().string(containsString("name=\"entity\" value=\"article\"")))
                .andExpect(content().string(containsString("value=\"fulltext\"")))
                .andExpect(content().string(containsString("value=\"semantic\"")))
                .andExpect(content().string(containsString("value=\"hybrid\"")))
                .andExpect(content().string(containsString("aria-label=\"Article search method\"")))
                .andExpect(content().string(containsString("name=\"q\"")))
                .andExpect(content().string(containsString("name=\"journalist\"")))
                .andExpect(content().string(containsString("value=\"title\"")))
                .andExpect(content().string(containsString("value=\"article_search_text\"")))
                .andExpect(content().string(not(containsString("Coming online in P7"))));
    }

    @Test
    void multimediaPanelIncludesVectorAndMediaFile() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"entity\" value=\"multimedia\"")))
                .andExpect(content().string(containsString("aria-label=\"Multimedia search method\"")))
                .andExpect(content().string(containsString("value=\"vector\"")))
                .andExpect(content().string(containsString("data-query-field")))
                .andExpect(content().string(containsString("value=\"multimedia.title\"")))
                .andExpect(content().string(containsString("type=\"file\"")))
                .andExpect(content().string(containsString("name=\"media\"")))
                // Both submit buttons read "Search"; assert the aria-labels so this keeps checking the
                // buttons rather than passing on the panel headings that carry the same words.
                .andExpect(content().string(containsString("aria-label=\"Search articles\">Search<")))
                .andExpect(content().string(containsString("aria-label=\"Search multimedia\">Search<")));
    }

    @Test
    void articlePanelDoesNotOfferVectorMode() throws Exception {
        String html = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int article = html.indexOf("panel--articles");
        int media = html.indexOf("panel--media");
        String articlePanel = html.substring(article, media);
        org.junit.jupiter.api.Assertions.assertFalse(
                articlePanel.contains("value=\"vector\""),
                "article panel must not offer mode=vector");
        org.junit.jupiter.api.Assertions.assertTrue(articlePanel.contains("value=\"fulltext\""));
        org.junit.jupiter.api.Assertions.assertTrue(articlePanel.contains("value=\"semantic\""));
        org.junit.jupiter.api.Assertions.assertTrue(articlePanel.contains("value=\"hybrid\""));
    }
}
