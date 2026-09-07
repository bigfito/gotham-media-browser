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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-slice tests for the journalist create flow (GET /journalist/new, POST /journalist). */
@WebMvcTest(JournalistController.class)
class JournalistCreateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JournalistRepository journalistRepository;

    @MockitoBean
    private JournalistService journalistService;

    @Test
    void newFormRendersEmptyWithChrome() throws Exception {
        mockMvc.perform(get("/journalist/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("journalist/new"))
                .andExpect(model().attributeExists("journalistForm"))
                .andExpect(content().string(containsString("New journalist")))
                .andExpect(content().string(containsString("Create journalist")))
                .andExpect(content().string(containsString("MIT License")));
    }

    @Test
    void validSubmitCreatesThenRedirectsToListWithFlash() throws Exception {
        when(journalistRepository.create(any(Journalist.class)))
                .thenAnswer(invocation -> ((Journalist) invocation.getArgument(0)).withId("new_id"));

        mockMvc.perform(post("/journalist")
                        .param("firstName", "Vicki")
                        .param("lastName", "Vale")
                        .param("email", "vicki@gotham.news")
                        .param("bio", "Photojournalist"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/journalist"))
                .andExpect(flash().attribute("flash", containsString("Vicki Vale")));

        verify(journalistRepository).create(any(Journalist.class));
    }

    @Test
    void invalidSubmitRedisplaysFormWithFieldErrorsAndDoesNotPersist() throws Exception {
        mockMvc.perform(post("/journalist")
                        .param("firstName", "")          // missing
                        .param("lastName", "Vale")
                        .param("email", "not-an-email"))  // bad format
                .andExpect(status().isOk())
                .andExpect(view().name("journalist/new"))
                .andExpect(model().attributeHasFieldErrors("journalistForm", "firstName", "email"))
                .andExpect(content().string(containsString("First name is required.")))
                .andExpect(content().string(containsString("Enter a valid email address.")));

        verify(journalistRepository, never()).create(any());
    }
}
