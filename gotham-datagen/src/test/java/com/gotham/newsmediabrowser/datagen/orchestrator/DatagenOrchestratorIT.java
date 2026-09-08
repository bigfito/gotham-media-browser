package com.gotham.newsmediabrowser.datagen.orchestrator;

import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.assumeHealthy;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.comfyuiUrl;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.kokoroUrl;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.ollamaUrl;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.textModel;
import static com.gotham.newsmediabrowser.datagen.DatagenITSupport.webUrl;
import static org.assertj.core.api.Assertions.assertThat;

import com.gotham.newsmediabrowser.datagen.DatagenConfig;
import com.gotham.newsmediabrowser.datagen.client.ComfyuiClient;
import com.gotham.newsmediabrowser.datagen.client.KokoroClient;
import com.gotham.newsmediabrowser.datagen.client.OllamaClient;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Small live orchestrator run through HTTP CRUD (1 journalist + 1 article). Skips unless
 * {@code gotham-web} and Ollama are up. Media helpers are optional — skip flags are set when
 * ComfyUI / Kokoro are down. Video is always skipped here (covered by {@code ComfyuiClientIT}).
 *
 * <pre>
 * mvn -Pit-datagen-helpers verify
 * </pre>
 */
@Tag("integration")
@Tag("datagen")
class DatagenOrchestratorIT {

    @Test
    void smallRunPostsJournalistAndArticleThroughHttp() {
        String web = webUrl();
        String ollama = ollamaUrl();
        DatagenWebClient webClient = new DatagenWebClient(web, Duration.ofSeconds(90), null);
        OllamaClient ollamaClient = new OllamaClient(ollama, textModel(), Duration.ofSeconds(180), null, null);
        assumeHealthy(webClient.isHealthy(), "gotham-web", web);
        assumeHealthy(ollamaClient.isHealthy(), "Ollama", ollama);

        ComfyuiClient comfyuiClient = new ComfyuiClient(comfyuiUrl(), Duration.ofSeconds(180), Duration.ofMillis(250), null, null);
        KokoroClient kokoroClient = new KokoroClient(kokoroUrl(), Duration.ofSeconds(90), null, null);
        boolean skipImage = !comfyuiClient.isHealthy();
        boolean skipAudio = !kokoroClient.isHealthy();

        List<String> journalistsBefore = webClient.listJournalistIds();
        List<String> articlesBefore = webClient.listArticleIds();

        DatagenConfig config = DatagenConfig.load(new String[]{
                "--web-base-url=" + web,
                "--ollama-url=" + ollama,
                "--comfyui-url=" + comfyuiUrl(),
                "--kokoro-url=" + kokoroUrl(),
                "--text-model=" + textModel(),
                "--journalists=1",
                "--articles=1",
                "--images-per-article=" + (skipImage ? "0" : "1"),
                "--audio-per-article=" + (skipAudio ? "0" : "1"),
                "--video-per-article=0",
                "--skip-image=" + skipImage,
                "--skip-audio=" + skipAudio,
                "--skip-video"
        });

        DatagenOrchestrator orchestrator = new DatagenOrchestrator(
                config, ollamaClient, comfyuiClient, kokoroClient, webClient, new PrintStream(System.out, true));

        try {
            DatagenOrchestrator.DatagenReport report = orchestrator.run();

            List<String> createdJournalists = newIds(journalistsBefore, webClient.listJournalistIds());
            List<String> createdArticles = newIds(articlesBefore, webClient.listArticleIds());

            assertThat(report.errors()).isEmpty();
            assertThat(report.journalistsCreated()).isEqualTo(1);
            assertThat(report.articlesCreated()).isEqualTo(1);
            assertThat(report.videoGenerated()).isZero();
            if (skipImage) {
                assertThat(report.imagesGenerated()).isZero();
            } else {
                assertThat(report.imagesGenerated()).isEqualTo(1);
            }
            if (skipAudio) {
                assertThat(report.audioGenerated()).isZero();
            } else {
                assertThat(report.audioGenerated()).isEqualTo(1);
            }
            assertThat(createdJournalists).hasSize(1);
            assertThat(createdArticles).hasSize(1);
        } finally {
            for (String articleId : newIds(articlesBefore, webClient.listArticleIds())) {
                try {
                    webClient.deleteArticle(articleId);
                } catch (RuntimeException cleanup) {
                    System.err.println("IT cleanup: could not delete article " + articleId + ": " + cleanup.getMessage());
                }
            }
            for (String journalistId : newIds(journalistsBefore, webClient.listJournalistIds())) {
                try {
                    webClient.deleteJournalist(journalistId);
                } catch (RuntimeException cleanup) {
                    System.err.println("IT cleanup: could not delete journalist " + journalistId + ": " + cleanup.getMessage());
                }
            }
        }
    }

    private static List<String> newIds(List<String> before, List<String> after) {
        Set<String> prior = new HashSet<>(before);
        List<String> created = new ArrayList<>();
        for (String id : after) {
            if (!prior.contains(id)) {
                created.add(id);
            }
        }
        return created;
    }
}
