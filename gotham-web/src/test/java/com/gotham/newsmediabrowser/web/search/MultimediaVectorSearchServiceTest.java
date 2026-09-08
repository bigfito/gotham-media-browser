package com.gotham.newsmediabrowser.web.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotham.newsmediabrowser.common.article.MultimediaSearchPage;
import com.gotham.newsmediabrowser.common.article.MultimediaSemanticSearchService;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties.Limit;
import com.gotham.newsmediabrowser.common.error.BadRequestException;
import com.gotham.newsmediabrowser.common.error.MediaLimitException;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/** Unit tests for the file-upload vector search path (cookbook §10): classify, size, embed, search. */
class MultimediaVectorSearchServiceTest {

    private final ImageBindClient imageBind = mock(ImageBindClient.class);
    private final MultimediaSemanticSearchService semantic = mock(MultimediaSemanticSearchService.class);
    private final MediaLimitsProperties limits = new MediaLimitsProperties(
            new Limit(DataSize.ofMegabytes(10), null),
            new Limit(DataSize.ofMegabytes(20), Duration.ofMinutes(5)),
            new Limit(DataSize.ofMegabytes(50), Duration.ofSeconds(90)));
    private final MultimediaVectorSearchService service =
            new MultimediaVectorSearchService(imageBind, limits, semantic);

    private static MultipartFile image(byte[] bytes) {
        return new MockMultipartFile("media", "x.png", "image/png", bytes);
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.search(image(new byte[0]),
                List.of(), null, null, null, null, List.of(), 1, 25))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnsupportedContentType() {
        MultipartFile pdf = new MockMultipartFile("media", "x.pdf", "application/pdf", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> service.search(pdf, List.of(), null, null, null, null, List.of(), 1, 25))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsOversizeFile() {
        MediaLimitsProperties tiny = new MediaLimitsProperties(
                new Limit(DataSize.ofBytes(2), null),
                new Limit(DataSize.ofMegabytes(20), Duration.ofMinutes(5)),
                new Limit(DataSize.ofMegabytes(50), Duration.ofSeconds(90)));
        MultimediaVectorSearchService strict = new MultimediaVectorSearchService(imageBind, tiny, semantic);

        assertThatThrownBy(() -> strict.search(image(new byte[] {1, 2, 3, 4}),
                List.of(), null, null, null, null, List.of(), 1, 25))
                .isInstanceOf(MediaLimitException.class);
    }

    @Test
    void embedsFileAndDelegatesToNestedVectorSearch() {
        float[] vector = new float[ImageBindClient.EMBEDDING_DIM];
        when(imageBind.embedMedia(eq(MediaType.IMAGE), any(), any(), eq("image/png"))).thenReturn(vector);
        MultimediaSearchPage page = new MultimediaSearchPage(List.of(), 0);
        when(semantic.searchByVector(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        MultimediaSearchPage result = service.search(image(new byte[] {1, 2, 3, 4}),
                List.of(), "Politics", "en", null, null, List.of(MediaType.IMAGE), 2, 50);

        assertThat(result).isSameAs(page);
        verify(imageBind).embedMedia(eq(MediaType.IMAGE), any(), eq("x.png"), eq("image/png"));
        verify(semantic).searchByVector(eq(vector), eq(List.of()), eq("Politics"), eq("en"),
                any(), any(), eq(List.of(MediaType.IMAGE)), eq(2), eq(50));
    }
}
