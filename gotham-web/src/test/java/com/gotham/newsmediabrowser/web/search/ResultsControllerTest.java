package com.gotham.newsmediabrowser.web.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.gotham.newsmediabrowser.common.article.Article;
import com.gotham.newsmediabrowser.common.article.ArticleFullTextQuery;
import com.gotham.newsmediabrowser.common.article.ArticleFullTextService;
import com.gotham.newsmediabrowser.common.article.ArticleHybridSearchService;
import com.gotham.newsmediabrowser.common.article.ArticleJournalist;
import com.gotham.newsmediabrowser.common.article.ArticleMetadata;
import com.gotham.newsmediabrowser.common.article.ArticleMultimedia;
import com.gotham.newsmediabrowser.common.article.ArticlePage;
import com.gotham.newsmediabrowser.common.article.ArticleSemanticSearchService;
import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.article.ContributionRole;
import com.gotham.newsmediabrowser.common.article.MultimediaFullTextQuery;
import com.gotham.newsmediabrowser.common.article.MultimediaFullTextService;
import com.gotham.newsmediabrowser.common.article.MultimediaHybridSearchService;
import com.gotham.newsmediabrowser.common.article.MultimediaSearchHit;
import com.gotham.newsmediabrowser.common.article.MultimediaSearchPage;
import com.gotham.newsmediabrowser.common.article.MultimediaSemanticSearchService;
import org.springframework.mock.web.MockMultipartFile;
import com.gotham.newsmediabrowser.common.journalist.Journalist;
import com.gotham.newsmediabrowser.common.media.MediaType;
import com.gotham.newsmediabrowser.common.search.SearchSort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-slice tests for {@code /results}: FTS wiring and filter/page query-param round-trips. */
@WebMvcTest(ResultsController.class)
class ResultsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleFullTextService articleFullTextService;

    @MockitoBean
    private MultimediaFullTextService multimediaFullTextService;

    @MockitoBean
    private ArticleSemanticSearchService articleSemanticSearchService;

    @MockitoBean
    private MultimediaSemanticSearchService multimediaSemanticSearchService;

    @MockitoBean
    private ArticleHybridSearchService articleHybridSearchService;

    @MockitoBean
    private MultimediaHybridSearchService multimediaHybridSearchService;

    @MockitoBean
    private MultimediaVectorSearchService multimediaVectorSearchService;

    private Article sampleArticle() {
        Journalist lois = new Journalist("j_lois", "Lois", "Lane", "lois@gotham.news", "Ace", null, null);
        return Article.newArticle(
                        "Gotham Transit Expansion Clears Final Vote",
                        null,
                        "City council approved funding.",
                        "Body",
                        "transit",
                        ArticleStatus.PUBLISHED,
                        "en",
                        Instant.parse("2026-09-06T00:00:00Z"),
                        new ArticleMetadata("Politics", List.of(), null, null, null, null, null, null),
                        List.of(ArticleJournalist.fromJournalist(lois, 0, ContributionRole.AUTHOR)))
                .withId("art-1");
    }

    @Test
    void articleFulltextRendersHitsAndChrome() throws Exception {
        when(articleFullTextService.search(any(ArticleFullTextQuery.class)))
                .thenReturn(new ArticlePage(List.of(sampleArticle()), 1));

        mockMvc.perform(get("/results")
                        .param("entity", "article")
                        .param("mode", "fulltext")
                        .param("q", "transit funding")
                        .param("fields", "title")
                        .param("fields", "body")
                        .param("journalist", "Lois Lane"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/articles"))
                .andExpect(content().string(containsString("Gotham News")))
                .andExpect(content().string(containsString("Entity · Articles")))
                .andExpect(content().string(containsString("transit funding")))
                .andExpect(content().string(containsString("Lois Lane")))
                .andExpect(content().string(containsString("Gotham Transit Expansion")))
                .andExpect(content().string(containsString("/article/art-1")))
                .andExpect(content().string(containsString("Journalist (full-text filter)")));
    }

    @Test
    void articleFiltersAndPaginationPreserveQueryParams() throws Exception {
        when(articleFullTextService.search(any(ArticleFullTextQuery.class)))
                .thenReturn(new ArticlePage(List.of(sampleArticle()), 80));

        String html = mockMvc.perform(get("/results")
                        .param("entity", "article")
                        .param("mode", "fulltext")
                        .param("q", "transit funding")
                        .param("fields", "title")
                        .param("status", "DRAFT")
                        .param("journalist", "Lois Lane")
                        .param("section", "Politics")
                        .param("language", "en")
                        .param("published_from", "2026-01-01")
                        .param("published_to", "2026-09-06")
                        .param("sort", "published_at_desc")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("name=\"q\" value=\"transit funding\"");
        assertThat(html).contains("value=\"Lois Lane\"");
        assertThat(html).contains("value=\"DRAFT\"");
        assertThat(html).contains("name=\"published_from\" value=\"2026-01-01\"");
        assertThat(html).contains("page=1");
        assertThat(html).contains("size=50");
        assertThat(html).contains("status=DRAFT");
        assertThat(html).contains("journalist=Lois");
        assertThat(html).contains("section=Politics");
        assertThat(html).contains("published_from=2026-01-01");
        assertThat(html).contains("fields=title");
        assertThat(html).contains("Prev");

        ArgumentCaptor<ArticleFullTextQuery> captor = ArgumentCaptor.forClass(ArticleFullTextQuery.class);
        verify(articleFullTextService).search(captor.capture());
        ArticleFullTextQuery query = captor.getValue();
        assertThat(query.q()).isEqualTo("transit funding");
        assertThat(query.fields()).containsExactly("title");
        assertThat(query.statuses()).containsExactly(ArticleStatus.DRAFT);
        assertThat(query.journalist()).isEqualTo("Lois Lane");
        assertThat(query.section()).isEqualTo("Politics");
        assertThat(query.language()).isEqualTo("en");
        assertThat(query.sort()).isEqualTo(SearchSort.PUBLISHED_AT_DESC);
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(50);
        assertThat(query.publishedFrom()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(query.publishedTo()).isEqualTo(Instant.parse("2026-09-06T23:59:59Z"));
    }

    @Test
    void emptyQueryDoesNotCallSearch() throws Exception {
        mockMvc.perform(get("/results").param("entity", "article").param("mode", "fulltext"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter a search query.")));

        verify(articleFullTextService, never()).search(any());
        verify(multimediaFullTextService, never()).search(any());
    }

    @Test
    void articleVectorModeIsBranded400() throws Exception {
        mockMvc.perform(get("/results")
                        .param("entity", "article")
                        .param("mode", "vector")
                        .param("q", "anything"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("Vector search is only available for multimedia.")));

        verify(articleFullTextService, never()).search(any());
    }

    @Test
    void multimediaFulltextRendersStorageUriPlayer() throws Exception {
        ArticleMultimedia image = new ArticleMultimedia(
                "m1", MediaType.IMAGE, "https://storage.googleapis.com/b/media/image/x.png",
                "image/png", 0, "Council chamber after the vote", null, "Chamber", null, "alt chamber",
                "x.png", 1L, null, 800, 500, null, null, null, null, null, null, null);
        MultimediaSearchHit hit = new MultimediaSearchHit(
                "art-1", "Gotham Transit Expansion", ArticleStatus.PUBLISHED, "Politics", "transit",
                Instant.parse("2026-09-06T00:00:00Z"), image);
        when(multimediaFullTextService.search(any(MultimediaFullTextQuery.class)))
                .thenReturn(new MultimediaSearchPage(List.of(hit), 1));

        mockMvc.perform(get("/results")
                        .param("entity", "multimedia")
                        .param("mode", "fulltext")
                        .param("q", "council chamber")
                        .param("mediaType", "IMAGE")
                        .param("fields", "multimedia.caption"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/multimedia"))
                .andExpect(content().string(containsString("https://storage.googleapis.com/b/media/image/x.png")))
                .andExpect(content().string(containsString("<img")))
                .andExpect(content().string(containsString("class=\"media-card__original\"")))
                .andExpect(content().string(containsString("target=\"gothamOriginalImage\"")))
                .andExpect(content().string(containsString("data-width=\"800\"")))
                .andExpect(content().string(containsString("data-height=\"500\"")))
                .andExpect(content().string(containsString("Council chamber after the vote")))
                .andExpect(content().string(containsString("/article/art-1")))
                .andExpect(content().string(containsString("name=\"mediaType\" value=\"IMAGE\"")));
    }

    @Test
    void multimediaFulltextRendersVideoOriginalLink() throws Exception {
        ArticleMultimedia video = new ArticleMultimedia(
                "m2", MediaType.VIDEO, "https://storage.googleapis.com/b/media/video/clip.mp4",
                "video/mp4", 0, "Council chamber after the vote", null, "Chamber clip", null, "alt chamber",
                "clip.mp4", 20L, null, 854, 480, 5000L, null, null, null, null, null, null);
        MultimediaSearchHit hit = new MultimediaSearchHit(
                "art-1", "Gotham Transit Expansion", ArticleStatus.PUBLISHED, "Politics", "transit",
                Instant.parse("2026-09-06T00:00:00Z"), video);
        when(multimediaFullTextService.search(any(MultimediaFullTextQuery.class)))
                .thenReturn(new MultimediaSearchPage(List.of(hit), 1));

        mockMvc.perform(get("/results")
                        .param("entity", "multimedia")
                        .param("mode", "fulltext")
                        .param("q", "council chamber")
                        .param("mediaType", "VIDEO")
                        .param("fields", "multimedia.caption"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/multimedia"))
                .andExpect(content().string(containsString("https://storage.googleapis.com/b/media/video/clip.mp4")))
                .andExpect(content().string(containsString("<video")))
                .andExpect(content().string(containsString("class=\"media-card__original\"")))
                .andExpect(content().string(containsString("target=\"gothamOriginalVideo\"")))
                .andExpect(content().string(containsString("data-kind=\"VIDEO\"")))
                .andExpect(content().string(containsString("data-width=\"854\"")))
                .andExpect(content().string(containsString("data-height=\"480\"")));
    }

    @Test
    void multimediaPaginationPreservesFilters() throws Exception {
        when(multimediaFullTextService.search(any(MultimediaFullTextQuery.class)))
                .thenReturn(new MultimediaSearchPage(List.of(), 60));

        String html = mockMvc.perform(get("/results")
                        .param("entity", "multimedia")
                        .param("mode", "fulltext")
                        .param("q", "council chamber")
                        .param("mediaType", "AUDIO")
                        .param("status", "PUBLISHED")
                        .param("page", "2")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("mediaType=AUDIO");
        assertThat(html).contains("status=PUBLISHED");
        assertThat(html).contains("q=council");
        assertThat(html).contains("page=1");

        ArgumentCaptor<MultimediaFullTextQuery> captor = ArgumentCaptor.forClass(MultimediaFullTextQuery.class);
        verify(multimediaFullTextService).search(captor.capture());
        assertThat(captor.getValue().mediaTypes()).containsExactly(MediaType.AUDIO);
        assertThat(captor.getValue().page()).isEqualTo(2);
    }

    @Test
    void articleSemanticModeDispatchesToSemanticService() throws Exception {
        when(articleSemanticSearchService.search(any(ArticleFullTextQuery.class)))
                .thenReturn(new ArticlePage(List.of(sampleArticle()), 1));

        mockMvc.perform(get("/results")
                        .param("entity", "article")
                        .param("mode", "semantic")
                        .param("q", "transit funding"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/articles"))
                .andExpect(content().string(containsString("Gotham Transit Expansion")))
                .andExpect(content().string(not(containsString("later phase"))));

        verify(articleSemanticSearchService).search(any(ArticleFullTextQuery.class));
        verify(articleFullTextService, never()).search(any());
    }

    @Test
    void multimediaSemanticModeDispatchesToSemanticService() throws Exception {
        ArticleMultimedia image = new ArticleMultimedia(
                "m1", MediaType.IMAGE, "https://storage.googleapis.com/b/media/image/x.png",
                "image/png", 0, "Council chamber after the vote", null, "Chamber", null, "alt chamber",
                "x.png", 1L, null, 800, 500, null, null, null, null, null, null, null);
        MultimediaSearchHit hit = new MultimediaSearchHit(
                "art-1", "Gotham Transit Expansion", ArticleStatus.PUBLISHED, "Politics", "transit",
                Instant.parse("2026-09-06T00:00:00Z"), image);
        when(multimediaSemanticSearchService.search(any(MultimediaFullTextQuery.class)))
                .thenReturn(new MultimediaSearchPage(List.of(hit), 1));

        mockMvc.perform(get("/results")
                        .param("entity", "multimedia")
                        .param("mode", "semantic")
                        .param("q", "council chamber"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/multimedia"))
                .andExpect(content().string(containsString("https://storage.googleapis.com/b/media/image/x.png")))
                .andExpect(content().string(not(containsString("later phase"))));

        verify(multimediaSemanticSearchService).search(any(MultimediaFullTextQuery.class));
        verify(multimediaFullTextService, never()).search(any());
    }

    @Test
    void articleHybridModeDispatchesToHybridService() throws Exception {
        when(articleHybridSearchService.search(any(ArticleFullTextQuery.class)))
                .thenReturn(new ArticlePage(List.of(sampleArticle()), 1));

        mockMvc.perform(get("/results")
                        .param("entity", "article")
                        .param("mode", "hybrid")
                        .param("q", "transit funding"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/articles"))
                .andExpect(content().string(containsString("Gotham Transit Expansion")))
                .andExpect(content().string(not(containsString("later phase"))));

        verify(articleHybridSearchService).search(any(ArticleFullTextQuery.class));
        verify(articleFullTextService, never()).search(any());
        verify(articleSemanticSearchService, never()).search(any());
    }

    @Test
    void multimediaHybridModeDispatchesToHybridService() throws Exception {
        when(multimediaHybridSearchService.search(any(MultimediaFullTextQuery.class)))
                .thenReturn(new MultimediaSearchPage(List.of(), 0));

        mockMvc.perform(get("/results")
                        .param("entity", "multimedia")
                        .param("mode", "hybrid")
                        .param("q", "council chamber"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/multimedia"))
                .andExpect(content().string(not(containsString("later phase"))));

        verify(multimediaHybridSearchService).search(any(MultimediaFullTextQuery.class));
        verify(multimediaFullTextService, never()).search(any());
        verify(multimediaSemanticSearchService, never()).search(any());
    }

    @Test
    void multimediaVectorModeWithoutFilePromptsForOne() throws Exception {
        mockMvc.perform(get("/results")
                        .param("entity", "multimedia")
                        .param("mode", "vector"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/multimedia"))
                .andExpect(content().string(containsString("Choose an image, audio, or video file")));

        verify(multimediaVectorSearchService, never()).search(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt());
    }

    @Test
    void multimediaVectorModePostWithFileDispatchesToVectorService() throws Exception {
        ArticleMultimedia image = new ArticleMultimedia(
                "m1", MediaType.IMAGE, "https://storage.googleapis.com/b/media/image/x.png",
                "image/png", 0, "Council chamber after the vote", null, "Chamber", null, "alt chamber",
                "x.png", 1L, null, 800, 500, null, null, null, null, null, null, null);
        MultimediaSearchHit hit = new MultimediaSearchHit(
                "art-1", "Gotham Transit Expansion", ArticleStatus.PUBLISHED, "Politics", "transit",
                Instant.parse("2026-09-06T00:00:00Z"), image);
        when(multimediaVectorSearchService.search(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt()))
                .thenReturn(new MultimediaSearchPage(List.of(hit), 1));

        MockMultipartFile file = new MockMultipartFile(
                "media", "chamber.png", "image/png", new byte[] {1, 2, 3, 4});

        mockMvc.perform(multipart("/results")
                        .file(file)
                        .param("entity", "multimedia")
                        .param("mode", "vector"))
                .andExpect(status().isOk())
                .andExpect(view().name("results/multimedia"))
                .andExpect(content().string(containsString("https://storage.googleapis.com/b/media/image/x.png")));

        verify(multimediaVectorSearchService).search(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt());
        verify(multimediaSemanticSearchService, never()).search(any());
        verify(multimediaHybridSearchService, never()).search(any());
    }
}
