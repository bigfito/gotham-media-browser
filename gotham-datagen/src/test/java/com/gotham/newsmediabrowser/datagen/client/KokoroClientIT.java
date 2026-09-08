package com.gotham.newsmediabrowser.datagen.client;

import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.assumeHealthy;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.kokoroUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live Kokoro TTS check (Compose {@code kokoro} on :8880). Skips when the container is down.
 *
 * <pre>
 * mvn -Pit-datagen-helpers verify
 * </pre>
 */
@Tag("integration")
@Tag("datagen")
class KokoroClientIT {

    @Test
    void healthAndSynthesizeSpeech() {
        String url = kokoroUrl();
        KokoroClient client = new KokoroClient(url, Duration.ofSeconds(90), null, null);
        assumeHealthy(client.isHealthy(), "Kokoro", url);

        GeneratedMedia audio = client.synthesizeSpeech(
                "Gotham Gazette evening bulletin.",
                "af_heart",
                "IT Bulletin",
                "Integration-test voice dispatch");

        assertThat(audio.bytes()).hasSizeGreaterThan(100);
        assertThat(audio.mimeType()).isEqualTo("audio/wav");
        String header = new String(audio.bytes(), 0, 4, StandardCharsets.ISO_8859_1);
        assertThat(header).isEqualTo("RIFF");
    }
}
