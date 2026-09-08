package com.gotham.newsmediabrowser.datagen.client;

import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.assumeHealthy;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.comfyuiLooksLikeStub;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.comfyuiUrl;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.runComfyuiVideo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live ComfyUI check (Compose {@code comfyui} on :8188). Skips when the container is down.
 *
 * <p>T2V (Wan) is minutes-per-clip on CPU. The video test runs against the in-repo stub, or when
 * {@code DATAGEN_IT_VIDEO=true} is set for a real-model lab run.
 *
 * <pre>
 * mvn -Pit-datagen-helpers verify
 * </pre>
 */
@Tag("integration")
@Tag("datagen")
class ComfyuiClientIT {

    @Test
    void healthAndTextToImage() {
        String url = comfyuiUrl();
        ComfyuiClient client = new ComfyuiClient(url, Duration.ofSeconds(180), Duration.ofMillis(250), null, null);
        assumeHealthy(client.isHealthy(), "ComfyUI", url);

        GeneratedMedia image = client.generateImage(
                "Gotham City Hall, documentary news photo",
                "blurry, low quality",
                "IT City Hall",
                "Integration-test still");

        assertThat(image.bytes()).isNotEmpty();
        assertThat(image.mimeType()).startsWith("image/");
        assertThat(image.filename()).isNotBlank();
    }

    @Test
    void healthAndTextToVideo() {
        String url = comfyuiUrl();
        Duration timeout = runComfyuiVideo() && !comfyuiLooksLikeStub(url)
                ? Duration.ofMinutes(10)
                : Duration.ofSeconds(60);
        ComfyuiClient client = new ComfyuiClient(url, timeout, Duration.ofMillis(250), null, null);
        assumeHealthy(client.isHealthy(), "ComfyUI", url);
        assumeTrue(
                runComfyuiVideo(),
                "Skipping T2V — set DATAGEN_IT_VIDEO=true for real Wan, or use COMFYUI_BACKEND=stub");

        GeneratedMedia video = client.generateVideo(
                "Gotham skyline, 5 second news establishing shot",
                "blurry, static",
                5,
                "IT Skyline",
                "Integration-test clip");

        assertThat(video.bytes()).isNotEmpty();
        assertThat(video.mimeType()).isEqualTo("video/mp4");
        assertThat(video.filename()).isNotBlank();
    }
}
