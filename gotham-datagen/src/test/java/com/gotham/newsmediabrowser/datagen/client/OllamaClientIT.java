package com.gotham.newsmediabrowser.datagen.client;

import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.assumeHealthy;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.ollamaUrl;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.textModel;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live Ollama check (Compose {@code ollama} on :11434). Skips when the container is down.
 *
 * <pre>
 * mvn -Pit-datagen-helpers verify
 * </pre>
 */
@Tag("integration")
@Tag("datagen")
class OllamaClientIT {

    @Test
    void healthAndChatCompletion() {
        String url = ollamaUrl();
        OllamaClient client = new OllamaClient(url, textModel(), Duration.ofSeconds(180), null, null);
        assumeHealthy(client.isHealthy(), "Ollama", url);

        String json = client.generateJson(
                "You are a test fixture. Reply with JSON only.",
                "Return a JSON object with keys ok (boolean true) and city (string Gotham).");

        assertThat(json).isNotBlank();
        assertThat(json).containsIgnoringCase("gotham");
    }
}
