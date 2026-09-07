package com.gotham.newsmediabrowser.web.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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

import com.gotham.newsmediabrowser.common.article.Article;
import com.gotham.newsmediabrowser.common.article.ArticleJournalist;
import com.gotham.newsmediabrowser.common.article.ArticleMetadata;
import com.gotham.newsmediabrowser.common.article.ArticlePage;
import com.gotham.newsmediabrowser.common.article.ArticleRepository;
import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.article.ContributionRole;
import com.gotham.newsmediabrowser.common.journalist.Journalist;
import com.gotham.newsmediabrowser.common.journalist.JournalistPage;
import com.gotham.newsmediabrowser.common.journalist.JournalistRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-slice tests for the article list + create/edit screens. */
@WebMvcTest(ArticleController.class)
class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleRepository articleRepository;

    @MockitoBean
    private JournalistRepository journalistRepository;

    private final Journalist lois =
            new Journalist("j_lois", "Lois", "Lane", "lois@gotham.news", "Ace", null, null);

    private void oneJournalistAvailable() {
        when(journalistRepository.findAll(eq(0), anyInt())).thenReturn(new JournalistPage(List.of(lois), 1));
    }

    @Test
    void listRendersArticlesWithStatusChipAndBylines() throws Exception {
        Article article = new Article("a1", "Transit vote", null, "s", "b", "transit",
                ArticleStatus.PUBLISHED, "en", null, null, null,
                new ArticleMetadata("Politics", List.of(), null, null, null, null, null, null),
                List.of(ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR)));
        when(articleRepository.findAll(null, null, 0, 25)).thenReturn(new ArticlePage(List.of(article), 1));

        mockMvc.perform(get("/article"))
                .andExpect(status().isOk())
                .andExpect(view().name("article/list"))
                .andExpect(content().string(containsString("Transit vote")))
                .andExpect(content().string(containsString("chip--published")))
                .andExpect(content().string(containsString("Lois Lane")))
                .andExpect(content().string(containsString("/article/a1")));
    }

    @Test
    void listStatusFilterIsPassedToTheRepository() throws Exception {
        when(articleRepository.findAll(ArticleStatus.DRAFT, null, 0, 50)).thenReturn(new ArticlePage(List.of(), 0));

        mockMvc.perform(get("/article").param("status", "DRAFT").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No articles yet")));

        verify(articleRepository).findAll(ArticleStatus.DRAFT, null, 0, 50);
    }

    @Test
    void newFormRendersWithJournalistOptions() throws Exception {
        oneJournalistAvailable();

        mockMvc.perform(get("/article/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("article/form"))
                .andExpect(content().string(containsString("New article")))
                .andExpect(content().string(containsString("Lois Lane")));
    }

    @Test
    void validCreateSnapshotsBylineThenRedirects() throws Exception {
        oneJournalistAvailable();
        when(articleRepository.create(any(Article.class)))
                .thenAnswer(invocation -> ((Article) invocation.getArgument(0)).withId("a_new"));

        mockMvc.perform(post("/article")
                        .param("title", "Bat-signal returns")
                        .param("summary", "A summary")
                        .param("body", "The body")
                        .param("status", "PUBLISHED")
                        .param("language", "en")
                        .param("section", "City")
                        .param("tags", "crime, gotham")
                        .param("journalistIds", "j_lois")
                        .param("bylineOrder[j_lois]", "1")
                        .param("role[j_lois]", "AUTHOR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/article"))
                .andExpect(flash().attribute("flash", containsString("Bat-signal returns")));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).create(captor.capture());
        Article saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(saved.metadata().tags()).containsExactly("crime", "gotham");
        assertThat(saved.journalists()).hasSize(1);
        ArticleJournalist byline = saved.journalists().get(0);
        assertThat(byline.journalistId()).isEqualTo("j_lois");
        assertThat(byline.fullName()).isEqualTo("Lois Lane");
        assertThat(byline.contributionRole()).isEqualTo(ContributionRole.AUTHOR);
    }

    @Test
    void invalidCreateMissingTitleStaysInForm() throws Exception {
        oneJournalistAvailable();

        mockMvc.perform(post("/article")
                        .param("title", "")
                        .param("summary", "s")
                        .param("body", "b")
                        .param("status", "DRAFT")
                        .param("journalistIds", "j_lois")
                        .param("bylineOrder[j_lois]", "1")
                        .param("role[j_lois]", "AUTHOR"))
                .andExpect(status().isOk())
                .andExpect(view().name("article/form"))
                .andExpect(model().attributeHasFieldErrors("articleForm", "title"));

        verify(articleRepository, never()).create(any());
    }

    @Test
    void createWithoutAnyJournalistIsRejected() throws Exception {
        oneJournalistAvailable();

        mockMvc.perform(post("/article")
                        .param("title", "No byline")
                        .param("summary", "s")
                        .param("body", "b")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(view().name("article/form"))
                .andExpect(model().attributeHasFieldErrors("articleForm", "journalistIds"));

        verify(articleRepository, never()).create(any());
    }

    @Test
    void editUnknownArticleRendersBranded404() throws Exception {
        when(articleRepository.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/article/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"));
    }
}
