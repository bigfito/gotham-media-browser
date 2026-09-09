package com.gotham.newsmediabrowser.web.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gotham.newsmediabrowser.common.article.ArticleMultimedia;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.imagebind.StubImageBindClient;
import com.gotham.newsmediabrowser.common.media.GcsStorageService;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** Unit tests for the media upload helper: embedding on upload (P6-T03) and removal (P5-T03). */
@ExtendWith(MockitoExtension.class)
class ArticleMediaUploadServiceTest {

    @Mock
    private GcsStorageService storageService;

    private final ImageBindClient imageBindClient = new StubImageBindClient();

    private ArticleMediaUploadService service() {
        return new ArticleMediaUploadService(storageService, imageBindClient);
    }

    private ArticleMultimedia element(String id, String uri) {
        return ArticleMultimedia.uploaded(id, MediaType.IMAGE, uri, "image/png", 0, id + ".png", 10L);
    }

    @Test
    void uploadEmbedsEachAssetIntoA1024DimVector() {
        when(storageService.upload(eq(MediaType.IMAGE), any(), any(), any(), any()))
                .thenReturn("https://storage.googleapis.com/b/media/image/x.png");
        MultipartFile file = new MockMultipartFile("mediaFiles", "x.png", "image/png", new byte[] {1, 2, 3});

        List<ArticleMultimedia> uploaded = service().upload(new MultipartFile[] {file}, 0);

        assertThat(uploaded).hasSize(1);
        assertThat(uploaded.get(0).assetVector()).hasSize(1024);
    }

    @Test
    void uploadAppliesDescriptiveMetadataInFileOrder() {
        when(storageService.upload(eq(MediaType.IMAGE), any(), any(), any(), any()))
                .thenReturn("https://storage.googleapis.com/b/media/image/x.png");
        MultipartFile file = new MockMultipartFile("mediaFiles", "x.png", "image/png", new byte[] {1, 2, 3});

        List<ArticleMultimedia> uploaded = service().upload(
                new MultipartFile[] {file}, 0,
                List.of("Chamber"), List.of("After the vote"), List.of(), List.of("alt"), List.of("Desk"));

        assertThat(uploaded.get(0).title()).isEqualTo("Chamber");
        assertThat(uploaded.get(0).caption()).isEqualTo("After the vote");
        assertThat(uploaded.get(0).altText()).isEqualTo("alt");
        assertThat(uploaded.get(0).credit()).isEqualTo("Desk");
    }

    @Test
    void uploadDeletesEarlierObjectsWhenALaterFileFails() {
        when(storageService.upload(eq(MediaType.IMAGE), any(), any(), any(), any()))
                .thenReturn("https://storage.googleapis.com/b/media/image/a.png")
                .thenThrow(new IllegalStateException("GCS down"));
        MultipartFile a = new MockMultipartFile("mediaFiles", "a.png", "image/png", new byte[] {1});
        MultipartFile b = new MockMultipartFile("mediaFiles", "b.png", "image/png", new byte[] {2});

        try {
            service().upload(new MultipartFile[] {a, b}, 0);
        } catch (IllegalStateException ignored) {
            // expected
        }

        verify(storageService).delete("https://storage.googleapis.com/b/media/image/a.png");
    }

    @Test
    void unsupportedTypeIsRejectedBeforeAnyUpload() {
        MultipartFile pdf = new MockMultipartFile("mediaFiles", "x.pdf", "application/pdf", new byte[] {1});
        MultipartFile png = new MockMultipartFile("mediaFiles", "x.png", "image/png", new byte[] {2});

        assertThatThrownBy(() -> service().upload(new MultipartFile[] {pdf, png}, 0))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void removeDeletesTheGcsObjectOfEveryElement() {
        ArticleMultimedia a = element("m1", "https://storage.googleapis.com/b/media/image/a.png");
        ArticleMultimedia b = element("m2", "https://storage.googleapis.com/b/media/image/b.png");

        service().remove(List.of(a, b));

        verify(storageService).delete(a.storageUri());
        verify(storageService).delete(b.storageUri());
    }

    @Test
    void removeWithNullListIsANoOp() {
        service().remove(null);

        verifyNoInteractions(storageService);
    }

    @Test
    void removeWithEmptyListTouchesNothing() {
        service().remove(List.of());

        verifyNoInteractions(storageService);
    }
}
