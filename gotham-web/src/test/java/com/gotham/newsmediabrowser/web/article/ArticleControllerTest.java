package com.gotham.newsmediabrowser.web.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import com.gotham.newsmediabrowser.common.article.ArticleMultimedia;
import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.article.ContributionRole;
import com.gotham.newsmediabrowser.common.journalist.Journalist;
import com.gotham.newsmediabrowser.common.journalist.JournalistPage;
import com.gotham.newsmediabrowser.common.journalist.JournalistRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.media.MediaType;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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

    @MockitoBean
    private ArticleMediaUploadService mediaUploadService;

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
                List.of(ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR)), List.of());
        when(articleRepository.findAll(null, null, 0, 25)).thenReturn(new ArticlePage(List.of(article), 1));

        mockMvc.perform(get("/article"))
                .andExpect(status().isOk())
                .andExpect(view().name("article/list"))
                .andExpect(content().string(containsString("Transit vote")))
                .andExpect(content().string(containsString("chip--published")))
                .andExpect(content().string(containsString("Lois Lane")))
                .andExpect(content().string(containsString("/article/a1")))
                .andExpect(content().string(containsString("/article/a1/view")))
                .andExpect(content().string(containsString("View")));
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
        verify(mediaUploadService, never()).upload(any(), anyInt(), any(), any(), any(), any(), any());
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
        verify(mediaUploadService, never()).upload(any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void editImagePreviewOpensOriginalInChildWindow() throws Exception {
        oneJournalistAvailable();
        com.gotham.newsmediabrowser.common.article.ArticleMultimedia photo =
                new com.gotham.newsmediabrowser.common.article.ArticleMultimedia(
                        "m1", com.gotham.newsmediabrowser.common.media.MediaType.IMAGE,
                        "https://storage.googleapis.com/b/media/image/x.png", "image/png", 0,
                        null, null, null, null, "Council chamber", "x.png", 10L, null,
                        512, 512, null, null, null, null, null, null, null);
        Article existing = new Article("a1", "Transit vote", null, "s", "b", "transit",
                ArticleStatus.PUBLISHED, "en", null, null, null,
                new ArticleMetadata("Politics", List.of(), null, null, null, null, null, null),
                List.of(ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR)),
                List.of(photo));
        when(articleRepository.findById("a1")).thenReturn(Optional.of(existing));

        mockMvc.perform(get("/article/a1"))
                .andExpect(status().isOk())
                .andExpect(view().name("article/form"))
                .andExpect(content().string(containsString("class=\"media-card__original\"")))
                .andExpect(content().string(containsString("href=\"https://storage.googleapis.com/b/media/image/x.png\"")))
                .andExpect(content().string(containsString("target=\"gothamOriginalImage\"")))
                .andExpect(content().string(containsString("data-width=\"512\"")))
                .andExpect(content().string(containsString("data-height=\"512\"")));
    }

    @Test
    void editVideoPreviewOpensOriginalInChildWindow() throws Exception {
        oneJournalistAvailable();
        com.gotham.newsmediabrowser.common.article.ArticleMultimedia clip =
                new com.gotham.newsmediabrowser.common.article.ArticleMultimedia(
                        "m2", com.gotham.newsmediabrowser.common.media.MediaType.VIDEO,
                        "https://storage.googleapis.com/b/media/video/clip.mp4", "video/mp4", 0,
                        null, null, null, null, "Council vote", "clip.mp4", 20L, null,
                        854, 480, 5000L, null, null, null, null, null, null);
        Article existing = new Article("a1", "Transit vote", null, "s", "b", "transit",
                ArticleStatus.PUBLISHED, "en", null, null, null,
                new ArticleMetadata("Politics", List.of(), null, null, null, null, null, null),
                List.of(ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR)),
                List.of(clip));
        when(articleRepository.findById("a1")).thenReturn(Optional.of(existing));

        mockMvc.perform(get("/article/a1"))
                .andExpect(status().isOk())
                .andExpect(view().name("article/form"))
                .andExpect(content().string(containsString("class=\"media-card__original\"")))
                .andExpect(content().string(containsString("href=\"https://storage.googleapis.com/b/media/video/clip.mp4\"")))
                .andExpect(content().string(containsString("target=\"gothamOriginalVideo\"")))
                .andExpect(content().string(containsString("data-kind=\"VIDEO\"")))
                .andExpect(content().string(containsString("data-width=\"854\"")))
                .andExpect(content().string(containsString("data-height=\"480\"")))
                .andExpect(content().string(containsString("<video")));
    }

    @Test
    void viewRendersFullArticleWithoutEditControls() throws Exception {
        oneJournalistAvailable();
        com.gotham.newsmediabrowser.common.article.ArticleMultimedia photo =
                new com.gotham.newsmediabrowser.common.article.ArticleMultimedia(
                        "m1", com.gotham.newsmediabrowser.common.media.MediaType.IMAGE,
                        "https://storage.googleapis.com/b/media/image/x.png", "image/png", 0,
                        "Council chamber after the vote", null, "Chamber", null, "alt chamber",
                        "x.png", 10L, null, 512, 512, null, null, null, null, null, null, null);
        Article existing = new Article("a1", "Transit vote", "A city-hall update",
                "Council funded the line.", "The body of the transit story.", "transit",
                ArticleStatus.PUBLISHED, "en", null, null, null,
                new ArticleMetadata("Politics", List.of("transit", "budget"), "Gotham City Hall",
                        "Gotham Gazette", null, null, null, null),
                List.of(ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR)),
                List.of(photo));
        when(articleRepository.findById("a1")).thenReturn(Optional.of(existing));

        mockMvc.perform(get("/article/a1/view"))
                .andExpect(status().isOk())
                .andExpect(view().name("article/view"))
                .andExpect(content().string(containsString("Transit vote")))
                .andExpect(content().string(containsString("The body of the transit story.")))
                .andExpect(content().string(containsString("Lois Lane")))
                .andExpect(content().string(containsString("Gotham City Hall")))
                .andExpect(content().string(containsString("class=\"media-card__original\"")))
                .andExpect(content().string(containsString("/article/a1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"title\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Remove on save"))));
    }

    @Test
    void viewUnknownArticleRendersBranded404() throws Exception {
        when(articleRepository.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/article/ghost/view"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"));
    }

    @Test
    void editUnknownArticleRendersBranded404() throws Exception {
        when(articleRepository.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/article/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"));
    }

    @Test
    void createWithMediaFileAttachesUploadedMultimedia() throws Exception {
        oneJournalistAvailable();
        com.gotham.newsmediabrowser.common.article.ArticleMultimedia uploaded =
                com.gotham.newsmediabrowser.common.article.ArticleMultimedia.uploaded(
                        "m1", com.gotham.newsmediabrowser.common.media.MediaType.IMAGE,
                        "https://storage.googleapis.com/b/media/image/x.png", "image/png", 0, "x.png", 10L);
        when(mediaUploadService.upload(any(), org.mockito.ArgumentMatchers.eq(0), any(), any(), any(), any(), any()))
                .thenReturn(java.util.List.of(uploaded));
        when(articleRepository.create(any(Article.class)))
                .thenAnswer(invocation -> ((Article) invocation.getArgument(0)).withId("a_new"));

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "mediaFiles", "x.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/article")
                        .file(file)
                        .param("title", "With media")
                        .param("summary", "s")
                        .param("body", "b")
                        .param("status", "DRAFT")
                        .param("newMediaCaption", "Press photo")
                        .param("journalistIds", "j_lois")
                        .param("bylineOrder[j_lois]", "1")
                        .param("role[j_lois]", "AUTHOR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/article"));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).create(captor.capture());
        assertThat(captor.getValue().multimedia()).hasSize(1);
        assertThat(captor.getValue().multimedia().get(0).storageUri())
                .isEqualTo("https://storage.googleapis.com/b/media/image/x.png");
    }

    @Test
    void deleteExistingArticlePurgesMediaThenRedirectsWithFlash() throws Exception {
        com.gotham.newsmediabrowser.common.article.ArticleMultimedia media =
                com.gotham.newsmediabrowser.common.article.ArticleMultimedia.uploaded(
                        "m1", com.gotham.newsmediabrowser.common.media.MediaType.IMAGE,
                        "https://storage.googleapis.com/b/media/image/x.png", "image/png", 0, "x.png", 10L);
        Article article = new Article("a1", "Transit vote", null, "s", "b", "transit",
                ArticleStatus.PUBLISHED, "en", null, null, null,
                new ArticleMetadata("Politics", List.of(), null, null, null, null, null, null),
                List.of(ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR)), List.of(media));
        when(articleRepository.findById("a1")).thenReturn(Optional.of(article));

        mockMvc.perform(post("/article/a1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/article"))
                .andExpect(flash().attribute("flash", containsString("Deleted")));

        // GCS objects are purged before the ES document is removed.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(mediaUploadService, articleRepository);
        inOrder.verify(mediaUploadService).remove(article.multimedia());
        inOrder.verify(articleRepository).deleteById("a1");
    }

    @Test
    void deleteUnknownArticleRendersBranded404() throws Exception {
        when(articleRepository.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/article/ghost/delete"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"));

        verify(articleRepository, never()).deleteById(any());
        verify(mediaUploadService, never()).remove(any());
    }

    @Test
    void updateRemovesSelectedMediaAndPurgesItsGcsObject() throws Exception {
        oneJournalistAvailable();
        com.gotham.newsmediabrowser.common.article.ArticleMultimedia keep =
                com.gotham.newsmediabrowser.common.article.ArticleMultimedia.uploaded(
                        "m_keep", com.gotham.newsmediabrowser.common.media.MediaType.IMAGE,
                        "https://storage.googleapis.com/b/media/image/keep.png", "image/png", 0, "keep.png", 10L);
        com.gotham.newsmediabrowser.common.article.ArticleMultimedia drop =
                com.gotham.newsmediabrowser.common.article.ArticleMultimedia.uploaded(
                        "m_drop", com.gotham.newsmediabrowser.common.media.MediaType.IMAGE,
                        "https://storage.googleapis.com/b/media/image/drop.png", "image/png", 1, "drop.png", 10L);
        Article existing = new Article("a1", "Transit vote", null, "s", "b", "transit",
                ArticleStatus.PUBLISHED, "en", null, null, null,
                new ArticleMetadata("Politics", List.of(), null, null, null, null, null, null),
                List.of(ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR)),
                List.of(keep, drop));
        when(articleRepository.findById("a1")).thenReturn(Optional.of(existing));
        when(mediaUploadService.upload(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(articleRepository.update(any(Article.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/article/a1")
                        .param("title", "Transit vote")
                        .param("summary", "s")
                        .param("body", "b")
                        .param("status", "PUBLISHED")
                        .param("journalistIds", "j_lois")
                        .param("bylineOrder[j_lois]", "1")
                        .param("role[j_lois]", "AUTHOR")
                        .param("removeMediaIds", "m_drop"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/article"));

        // The dropped element's GCS object is deleted; the kept one stays on the saved document.
        verify(mediaUploadService).remove(List.of(drop));
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).update(captor.capture());
        assertThat(captor.getValue().multimedia())
                .extracting(com.gotham.newsmediabrowser.common.article.ArticleMultimedia::multimediaElementId)
                .containsExactly("m_keep");
    }

    /** Existing element carrying every descriptive field, used by the edit round-trip tests. */
    private ArticleMultimedia captioned() {
        return new ArticleMultimedia(
                "m_keep", MediaType.IMAGE, "https://storage.googleapis.com/b/media/image/keep.png",
                "image/png", 0, "Council chamber after the vote", "Vicki Vale", "Chamber",
                "Wide shot of the chamber", "alt chamber", "keep.png", 10L, null,
                800, 500, null, null, null, null, null, null, null);
    }

    private Article articleWithMedia(List<ArticleMultimedia> media) {
        return new Article("a1", "Transit vote", null, "s", "b", "transit",
                ArticleStatus.PUBLISHED, "en", null, null, null,
                new ArticleMetadata("Politics", List.of(), null, null, null, null, null, null),
                List.of(ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR)),
                media);
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder editRequest() {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/article/a1")
                .param("title", "Transit vote")
                .param("summary", "s")
                .param("body", "b")
                .param("status", "PUBLISHED")
                .param("journalistIds", "j_lois")
                .param("bylineOrder[j_lois]", "1")
                .param("role[j_lois]", "AUTHOR");
    }

    @Test
    void editPostWithoutCaptionParamsKeepsStoredDescriptiveText() throws Exception {
        // Datagen, curl and the smoke script post the article fields without the media*[id] maps.
        // Those assets must keep their stored text instead of being silently blanked.
        oneJournalistAvailable();
        when(articleRepository.findById("a1")).thenReturn(Optional.of(articleWithMedia(List.of(captioned()))));
        when(mediaUploadService.upload(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(articleRepository.update(any(Article.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(editRequest())
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).update(captor.capture());
        ArticleMultimedia saved = captor.getValue().multimedia().get(0);
        assertThat(saved.caption()).isEqualTo("Council chamber after the vote");
        assertThat(saved.title()).isEqualTo("Chamber");
        assertThat(saved.description()).isEqualTo("Wide shot of the chamber");
        assertThat(saved.altText()).isEqualTo("alt chamber");
        assertThat(saved.credit()).isEqualTo("Vicki Vale");
    }

    @Test
    void editPostWithCaptionParamsStillClearsAndUpdatesThem() throws Exception {
        // The browser form does render every field, so blanking one there must still blank it.
        oneJournalistAvailable();
        when(articleRepository.findById("a1")).thenReturn(Optional.of(articleWithMedia(List.of(captioned()))));
        when(mediaUploadService.upload(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(articleRepository.update(any(Article.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(editRequest()
                        .param("mediaCaption[m_keep]", "Reworded caption")
                        .param("mediaTitle[m_keep]", "")
                        .param("mediaDescription[m_keep]", "Wide shot of the chamber")
                        .param("mediaAltText[m_keep]", "alt chamber")
                        .param("mediaCredit[m_keep]", "Vicki Vale"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).update(captor.capture());
        ArticleMultimedia saved = captor.getValue().multimedia().get(0);
        assertThat(saved.caption()).isEqualTo("Reworded caption");
        assertThat(saved.title()).isNull();
    }

    @Test
    void editPurgesRemovedObjectsOnlyAfterTheDocumentIsSaved() throws Exception {
        oneJournalistAvailable();
        ArticleMultimedia drop = ArticleMultimedia.uploaded("m_drop", MediaType.IMAGE,
                "https://storage.googleapis.com/b/media/image/drop.png", "image/png", 1, "drop.png", 10L);
        when(articleRepository.findById("a1"))
                .thenReturn(Optional.of(articleWithMedia(List.of(captioned(), drop))));
        when(mediaUploadService.upload(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(articleRepository.update(any(Article.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(editRequest().param("removeMediaIds", "m_drop"))
                .andExpect(status().is3xxRedirection());

        // Purging first would leave the document pointing at deleted objects if the save failed.
        InOrder order = inOrder(articleRepository, mediaUploadService);
        order.verify(articleRepository).update(any(Article.class));
        order.verify(mediaUploadService).remove(List.of(drop));
    }

    @Test
    void editKeepsRemovedObjectsWhenTheSaveFails() throws Exception {
        oneJournalistAvailable();
        ArticleMultimedia drop = ArticleMultimedia.uploaded("m_drop", MediaType.IMAGE,
                "https://storage.googleapis.com/b/media/image/drop.png", "image/png", 1, "drop.png", 10L);
        when(articleRepository.findById("a1"))
                .thenReturn(Optional.of(articleWithMedia(List.of(captioned(), drop))));
        when(mediaUploadService.upload(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(articleRepository.update(any(Article.class)))
                .thenThrow(new DependencyException("Elasticsearch", new RuntimeException("boom")));

        mockMvc.perform(editRequest().param("removeMediaIds", "m_drop"))
                .andExpect(status().isServiceUnavailable());

        // The stored document still references drop.png, so its object must survive for the retry.
        verify(mediaUploadService, never()).remove(List.of(drop));
    }
}
