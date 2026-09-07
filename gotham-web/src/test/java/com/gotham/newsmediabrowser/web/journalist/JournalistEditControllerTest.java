package com.gotham.newsmediabrowser.web.journalist;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.gotham.newsmediabrowser.common.journalist.Journalist;
import com.gotham.newsmediabrowser.common.journalist.JournalistRepository;
import com.gotham.newsmediabrowser.common.journalist.JournalistService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-slice tests for the journalist edit + cascade-strip delete flows. */
@WebMvcTest(JournalistController.class)
class JournalistEditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JournalistRepository journalistRepository;

    @MockitoBean
    private JournalistService journalistService;

    private Journalist existing() {
        return new Journalist("j_lois", "Lois", "Lane", "lois@gotham.news", "Ace reporter",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-02T00:00:00Z"));
    }

    @Test
    void editFormPrefillsExistingValues() throws Exception {
        when(journalistRepository.findById("j_lois")).thenReturn(Optional.of(existing()));

        mockMvc.perform(get("/journalist/j_lois"))
                .andExpect(status().isOk())
                .andExpect(view().name("journalist/edit"))
                .andExpect(content().string(containsString("value=\"Lois\"")))
                .andExpect(content().string(containsString("j_lois")))
                .andExpect(content().string(containsString("Delete journalist")));
    }

    @Test
    void editFormForUnknownIdRendersBranded404() throws Exception {
        when(journalistRepository.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/journalist/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"));
    }

    @Test
    void validUpdateCascadesThenRedirectsWithFlash() throws Exception {
        when(journalistRepository.findById("j_lois")).thenReturn(Optional.of(existing()));
        when(journalistService.update(any(Journalist.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/journalist/j_lois")
                        .param("firstName", "Louise")
                        .param("lastName", "Lane")
                        .param("email", "louise@gotham.news")
                        .param("bio", "Senior reporter"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/journalist"))
                .andExpect(flash().attribute("flash", containsString("Louise Lane")));

        verify(journalistService).update(any(Journalist.class));
    }

    @Test
    void invalidUpdateRedisplaysEditFormAndDoesNotCascade() throws Exception {
        when(journalistRepository.findById("j_lois")).thenReturn(Optional.of(existing()));

        mockMvc.perform(post("/journalist/j_lois")
                        .param("firstName", "")
                        .param("lastName", "Lane")
                        .param("email", "bad"))
                .andExpect(status().isOk())
                .andExpect(view().name("journalist/edit"))
                .andExpect(model().attributeHasFieldErrors("journalistForm", "firstName", "email"));

        verify(journalistService, never()).update(any());
    }

    @Test
    void deleteCascadeStripsThenRedirectsWithFlash() throws Exception {
        mockMvc.perform(post("/journalist/j_lois/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/journalist"))
                .andExpect(flash().attribute("flash", containsString("stripped")));

        verify(journalistService).cascadeDelete("j_lois");
    }
}
