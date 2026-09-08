package com.gotham.newsmediabrowser.common.imagebind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.gotham.newsmediabrowser.common.config.ImageBindProperties;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live check against a running {@code imagebind-service}. Skipped (not failed) unless
 * {@code IMAGEBIND_BASE_URL} is set, so it never blocks {@code mvn test} or CI.
 *
 * <pre>
 * IMAGEBIND_BASE_URL=http://127.0.0.1:8081 \
 *   mvn -pl gotham-common test -Dtest=HttpImageBindClientIT -DfailIfNoTests=false
 * </pre>
 */
@Tag("integration")
@Tag("imagebind")
class HttpImageBindClientIT {

    private static final String BASE_URL = System.getenv("IMAGEBIND_BASE_URL");

    private HttpImageBindClient client() {
        return new HttpImageBindClient(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
                new ImageBindProperties(BASE_URL, false, Duration.ofSeconds(120)));
    }

    @Test
    void embedsTextAgainstLiveService() {
        assumeTrue(BASE_URL != null && !BASE_URL.isBlank(), "IMAGEBIND_BASE_URL not set");

        float[] vector = client().embedText("gotham transit vote");

        assertThat(vector).hasSize(ImageBindClient.EMBEDDING_DIM);
    }

    @Test
    void embedsImageAgainstLiveService() {
        assumeTrue(BASE_URL != null && !BASE_URL.isBlank(), "IMAGEBIND_BASE_URL not set");
        // Minimal 1x1 PNG.
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgYGAAAAAEAAEnNCcKAAAAAElFTkSuQmCC");

        float[] vector = client().embedMedia(MediaType.IMAGE, png, "px.png", "image/png");

        assertThat(vector).hasSize(ImageBindClient.EMBEDDING_DIM);
    }
}
