package com.gotham.newsmediabrowser.web.article;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gotham.newsmediabrowser.common.article.ArticleMultimedia;
import com.gotham.newsmediabrowser.common.media.GcsStorageService;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the media upload helper's removal behaviour (P5-T03). */
@ExtendWith(MockitoExtension.class)
class ArticleMediaUploadServiceTest {

    @Mock
    private GcsStorageService storageService;

    private ArticleMultimedia element(String id, String uri) {
        return ArticleMultimedia.uploaded(id, MediaType.IMAGE, uri, "image/png", 0, id + ".png", 10L);
    }

    @Test
    void removeDeletesTheGcsObjectOfEveryElement() {
        ArticleMediaUploadService service = new ArticleMediaUploadService(storageService);
        ArticleMultimedia a = element("m1", "https://storage.googleapis.com/b/media/image/a.png");
        ArticleMultimedia b = element("m2", "https://storage.googleapis.com/b/media/image/b.png");

        service.remove(List.of(a, b));

        verify(storageService).delete(a.storageUri());
        verify(storageService).delete(b.storageUri());
    }

    @Test
    void removeWithNullListIsANoOp() {
        ArticleMediaUploadService service = new ArticleMediaUploadService(storageService);

        service.remove(null);

        verifyNoInteractions(storageService);
    }

    @Test
    void removeWithEmptyListTouchesNothing() {
        ArticleMediaUploadService service = new ArticleMediaUploadService(storageService);

        service.remove(List.of());

        verifyNoInteractions(storageService);
    }
}
