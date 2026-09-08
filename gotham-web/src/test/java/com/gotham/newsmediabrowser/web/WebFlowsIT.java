package com.gotham.newsmediabrowser.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-context web flows against a real cluster (P9-T03): the whole application (real Elasticsearch
 * client, index bootstrap runner, error handling) is wired and driven through MockMvc. Enabled only
 * when {@code ES_ENDPOINT} / {@code ES_API_KEY} are set, so it never loads a live context in CI.
 * The app reads its real credentials from {@code application-local.properties}; the env vars gate the
 * test the same way the repository ITs are gated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "ES_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ES_API_KEY", matches = ".+")
class WebFlowsIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void landingRendersChrome() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Gotham")));
    }

    @Test
    void journalistListRendersAgainstLiveEs() throws Exception {
        mockMvc.perform(get("/journalist"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Gotham")));
    }

    @Test
    void articleFullTextResultsRenderAgainstLiveEs() throws Exception {
        mockMvc.perform(get("/results")
                        .param("entity", "article")
                        .param("mode", "fulltext")
                        .param("q", "gotham"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/articles"));
    }

    @Test
    void articleVectorModeIsBranded400() throws Exception {
        mockMvc.perform(get("/results")
                        .param("entity", "article")
                        .param("mode", "vector")
                        .param("q", "anything"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error"))
                .andExpect(content().string(Matchers.containsString("Vector search is only available for multimedia.")));
    }
}
