package com.gotham.newsmediabrowser.common.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.gotham.newsmediabrowser.common.config.GcsProperties;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties.Limit;
import com.gotham.newsmediabrowser.common.error.MediaLimitException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

/** Unit tests for limit enforcement and object/URI handling (no real GCS). */
@ExtendWith(MockitoExtension.class)
class GcsStorageServiceTest {

    @Mock
    private Storage storage;

    private final GcsProperties gcs = new GcsProperties("elastic-sa", "test-bucket", "secrets/gcp-sa.json");
    private final MediaLimitsProperties limits = new MediaLimitsProperties(
            new Limit(DataSize.ofMegabytes(10), null),
            new Limit(DataSize.ofMegabytes(20), Duration.ofMinutes(5)),
            new Limit(DataSize.ofMegabytes(50), Duration.ofSeconds(90)));

    private GcsStorageService service() {
        return new GcsStorageService(storage, gcs, limits);
    }

    @Test
    void uploadWithinLimitsStoresAndReturnsPublicUri() {
        byte[] data = new byte[1024];
        String uri = service().upload(MediaType.IMAGE, "photo.png", "image/png", data, null);

        assertThat(uri).startsWith("https://storage.googleapis.com/test-bucket/media/image/")
                .endsWith("-photo.png");

        ArgumentCaptor<BlobInfo> blob = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).create(blob.capture(), any(byte[].class));
        assertThat(blob.getValue().getContentType()).isEqualTo("image/png");
        assertThat(blob.getValue().getBucket()).isEqualTo("test-bucket");
    }

    @Test
    void oversizeImageIsRejectedBeforeUpload() {
        byte[] tooBig = new byte[(int) (DataSize.ofMegabytes(10).toBytes() + 1)];

        assertThatThrownBy(() -> service().upload(MediaType.IMAGE, "big.png", "image/png", tooBig, null))
                .isInstanceOf(MediaLimitException.class)
                .hasMessageContaining("over the");

        verify(storage, never()).create(any(BlobInfo.class), any(byte[].class));
    }

    @Test
    void overlongVideoIsRejectedBeforeUpload() {
        byte[] small = new byte[10];

        assertThatThrownBy(() ->
                service().upload(MediaType.VIDEO, "clip.mp4", "video/mp4", small, Duration.ofSeconds(120)))
                .isInstanceOf(MediaLimitException.class)
                .hasMessageContaining("90s");

        verify(storage, never()).create(any(BlobInfo.class), any(byte[].class));
    }

    @Test
    void audioWithoutDurationIsRejectedBeforeUpload() {
        assertThatThrownBy(() -> service().upload(MediaType.AUDIO, "clip.mp3", "audio/mpeg", new byte[10], null))
                .isInstanceOf(MediaLimitException.class)
                .hasMessageContaining("could not be determined");

        verify(storage, never()).create(any(BlobInfo.class), any(byte[].class));
    }

    @Test
    void deleteParsesObjectNameFromOurUri() {
        String uri = "https://storage.googleapis.com/test-bucket/media/audio/uuid-clip.mp3";
        when(storage.delete(any(BlobId.class))).thenReturn(true);

        assertThat(service().delete(uri)).isTrue();

        ArgumentCaptor<BlobId> id = ArgumentCaptor.forClass(BlobId.class);
        verify(storage).delete(id.capture());
        assertThat(id.getValue().getName()).isEqualTo("media/audio/uuid-clip.mp3");
    }

    @Test
    void deleteIgnoresForeignUri() {
        assertThat(service().delete("https://example.com/other/thing.png")).isFalse();
        verify(storage, never()).delete(any(BlobId.class));
    }

    @Test
    void mediaTypeIsClassifiedFromContentType() {
        assertThat(MediaType.fromContentType("image/jpeg")).isEqualTo(MediaType.IMAGE);
        assertThat(MediaType.fromContentType("audio/mpeg")).isEqualTo(MediaType.AUDIO);
        assertThat(MediaType.fromContentType("video/mp4")).isEqualTo(MediaType.VIDEO);
        assertThatThrownBy(() -> MediaType.fromContentType("application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
