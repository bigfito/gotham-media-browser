package com.gotham.newsmediabrowser.web.journalist;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.gotham.newsmediabrowser.common.journalist.Journalist;
import com.gotham.newsmediabrowser.common.journalist.JournalistPage;
import com.gotham.newsmediabrowser.common.journalist.JournalistRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-slice tests for the journalist list page: chrome, empty/non-empty states, and pagination. */
@WebMvcTest(JournalistController.class)
class JournalistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JournalistRepository journalistRepository;

    @Test
    void rendersJournalistsWithChromeAndPagingMeta() throws Exception {
        Journalist lois = new Journalist("j_lois", "Lois", "Lane", "lois@gotham.news", "Ace",
                Instant.parse("2026-09-06T00:00:00Z"), Instant.parse("2026-09-06T00:00:00Z"));
        Journalist clark = new Journalist("j_clark", "Clark", "Kent", "clark@gotham.news", "Mild",
                Instant.parse("2026-09-04T00:00:00Z"), Instant.parse("2026-09-04T00:00:00Z"));
        when(journalistRepository.findAll(0, 25)).thenReturn(new JournalistPage(List.of(lois, clark), 2));

        mockMvc.perform(get("/journalist"))
                .andExpect(status().isOk())
                .andExpect(view().name("journalist/list"))
                .andExpect(model().attribute("activePage", "journalist"))
                // shared chrome
                .andExpect(content().string(containsString("Gotham News")))
                .andExpect(content().string(containsString("MIT License")))
                // data
                .andExpect(content().string(containsString("Lois Lane")))
                .andExpect(content().string(containsString("clark@gotham.news")))
                .andExpect(content().string(containsString("j_lois")))
                // delete action targets the id-based route
                .andExpect(content().string(containsString("/journalist/j_lois/delete")))
                // paging meta
                .andExpect(content().string(containsString("showing")))
                .andExpect(content().string(containsString("size=25")));
    }

    @Test
    void rendersEmptyStateWhenNoJournalists() throws Exception {
        when(journalistRepository.findAll(0, 25)).thenReturn(new JournalistPage(List.of(), 0));

        mockMvc.perform(get("/journalist"))
                .andExpect(status().isOk())
                .andExpect(view().name("journalist/list"))
                .andExpect(content().string(containsString("No journalists yet")))
                // no table rendered when empty
                .andExpect(content().string(not(containsString("data-table__actions"))));
    }

    @Test
    void secondPageWithLargerSizeComputesFromOffset() throws Exception {
        when(journalistRepository.findAll(50, 50)).thenReturn(new JournalistPage(List.of(), 200));

        mockMvc.perform(get("/journalist").param("page", "2").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("from", 50))
                .andExpect(model().attribute("size", 50));

        // from = (page - 1) * size = (2 - 1) * 50
        verify(journalistRepository).findAll(50, 50);
    }

    @Test
    void invalidSizeFallsBackToDefault() throws Exception {
        when(journalistRepository.findAll(0, 25)).thenReturn(new JournalistPage(List.of(), 0));

        mockMvc.perform(get("/journalist").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("size", 25));

        verify(journalistRepository).findAll(0, 25);
    }

    @Test
    void nonPositivePageIsClampedToOne() throws Exception {
        when(journalistRepository.findAll(anyInt(), anyInt())).thenReturn(new JournalistPage(List.of(), 0));

        mockMvc.perform(get("/journalist").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", 1));

        verify(journalistRepository).findAll(0, 25);
    }
}
