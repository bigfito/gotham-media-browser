package com.gotham.newsmediabrowser.datagen.orchestrator;

import com.gotham.newsmediabrowser.datagen.DatagenConfig;
import com.gotham.newsmediabrowser.datagen.client.ComfyuiClient;
import com.gotham.newsmediabrowser.datagen.client.DatagenClientException;
import com.gotham.newsmediabrowser.datagen.client.GeneratedMedia;
import com.gotham.newsmediabrowser.datagen.client.KokoroClient;
import com.gotham.newsmediabrowser.datagen.client.OllamaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatagenOrchestratorTest {

    private ByteArrayOutputStream outStream;
    private PrintStream out;

    @BeforeEach
    void setUp() {
        outStream = new ByteArrayOutputStream();
        out = new PrintStream(outStream, true, StandardCharsets.UTF_8);
    }

    @Test
    void run_whenWebDown_throwsDatagenClientException() {
        DatagenConfig config = DatagenConfig.defaultsOnly();
        OllamaClient ollama = new FakeOllamaClient(true);
        ComfyuiClient comfyui = new FakeComfyuiClient(true);
        KokoroClient kokoro = new FakeKokoroClient(true);
        DatagenWebClient web = new FakeDatagenWebClient(false);

        DatagenOrchestrator orchestrator = new DatagenOrchestrator(config, ollama, comfyui, kokoro, web, out);

        assertThatThrownBy(orchestrator::run)
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("gotham-web");
    }

    @Test
    void run_whenOllamaDown_throwsDatagenClientException() {
        DatagenConfig config = DatagenConfig.defaultsOnly();
        OllamaClient ollama = new FakeOllamaClient(false);
        ComfyuiClient comfyui = new FakeComfyuiClient(true);
        KokoroClient kokoro = new FakeKokoroClient(true);
        DatagenWebClient web = new FakeDatagenWebClient(true);

        DatagenOrchestrator orchestrator = new DatagenOrchestrator(config, ollama, comfyui, kokoro, web, out);

        assertThatThrownBy(orchestrator::run)
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("Ollama");
    }

    @Test
    void run_whenComfyuiDownAndNotSkipped_throwsDatagenClientException() {
        DatagenConfig config = DatagenConfig.load(new String[]{"--skip-image=false"});
        OllamaClient ollama = new FakeOllamaClient(true);
        ComfyuiClient comfyui = new FakeComfyuiClient(false);
        KokoroClient kokoro = new FakeKokoroClient(true);
        DatagenWebClient web = new FakeDatagenWebClient(true);

        DatagenOrchestrator orchestrator = new DatagenOrchestrator(config, ollama, comfyui, kokoro, web, out);

        assertThatThrownBy(orchestrator::run)
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("ComfyUI");
    }

    @Test
    void run_whenKokoroDownAndNotSkipped_throwsDatagenClientException() {
        DatagenConfig config = DatagenConfig.load(new String[]{"--skip-audio=false"});
        OllamaClient ollama = new FakeOllamaClient(true);
        ComfyuiClient comfyui = new FakeComfyuiClient(true);
        KokoroClient kokoro = new FakeKokoroClient(false);
        DatagenWebClient web = new FakeDatagenWebClient(true);

        DatagenOrchestrator orchestrator = new DatagenOrchestrator(config, ollama, comfyui, kokoro, web, out);

        assertThatThrownBy(orchestrator::run)
                .isInstanceOf(DatagenClientException.class)
                .hasMessageContaining("Kokoro");
    }

    @Test
    void run_success_generatesJournalistsArticlesAndMedia() {
        DatagenConfig config = DatagenConfig.load(new String[]{
                "--journalists=2",
                "--articles=2",
                "--images-per-article=1",
                "--audio-per-article=1",
                "--video-per-article=1"
        });

        OllamaClient ollama = new FakeOllamaClient(true);
        ComfyuiClient comfyui = new FakeComfyuiClient(true);
        KokoroClient kokoro = new FakeKokoroClient(true);
        DatagenWebClient web = new FakeDatagenWebClient(true);

        DatagenOrchestrator orchestrator = new DatagenOrchestrator(config, ollama, comfyui, kokoro, web, out);
        DatagenOrchestrator.DatagenReport report = orchestrator.run();

        assertThat(report.journalistsCreated()).isEqualTo(2);
        assertThat(report.articlesCreated()).isEqualTo(2);
        assertThat(report.imagesGenerated()).isEqualTo(2);
        assertThat(report.audioGenerated()).isEqualTo(2);
        assertThat(report.videoGenerated()).isEqualTo(2);
        assertThat(report.errors()).isEmpty();
    }

    @Test
    void run_success_skipFlagsHonored() {
        DatagenConfig config = DatagenConfig.load(new String[]{
                "--journalists=1",
                "--articles=1",
                "--skip-image",
                "--skip-audio",
                "--skip-video"
        });

        OllamaClient ollama = new FakeOllamaClient(true);
        ComfyuiClient comfyui = new FakeComfyuiClient(false);
        KokoroClient kokoro = new FakeKokoroClient(false);
        DatagenWebClient web = new FakeDatagenWebClient(true);

        DatagenOrchestrator orchestrator = new DatagenOrchestrator(config, ollama, comfyui, kokoro, web, out);
        DatagenOrchestrator.DatagenReport report = orchestrator.run();

        assertThat(report.journalistsCreated()).isEqualTo(1);
        assertThat(report.articlesCreated()).isEqualTo(1);
        assertThat(report.imagesGenerated()).isZero();
        assertThat(report.audioGenerated()).isZero();
        assertThat(report.videoGenerated()).isZero();
        assertThat(report.errors()).isEmpty();
    }

    // --- Fake Client Implementations for in-memory fast testing ---

    private static class FakeOllamaClient extends OllamaClient {
        private final boolean healthy;

        public FakeOllamaClient(boolean healthy) {
            super("http://fake-ollama:11434", "qwen2.5:7b-instruct");
            this.healthy = healthy;
        }

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public String generateJson(String systemPrompt, String userPrompt) {
            if (userPrompt.contains("journalist")) {
                return "{\"firstName\":\"Clark\",\"lastName\":\"Kent\",\"email\":\"clark.kent@gothamgazette.com\",\"bio\":\"Reporter\"}";
            }
            return "{\"title\":\"Gotham Transit Funding\",\"summary\":\"Council voted today.\",\"body\":\"Details on the funding package.\",\"tags\":\"transit,city\",\"location\":\"Gotham City Hall\",\"imagePrompt\":\"Photo of City Hall\",\"audioScript\":\"Voice report.\",\"videoPrompt\":\"Subway arrival 5s\"}";
        }
    }

    private static class FakeComfyuiClient extends ComfyuiClient {
        private final boolean healthy;

        public FakeComfyuiClient(boolean healthy) {
            super("http://fake-comfyui:8188");
            this.healthy = healthy;
        }

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public GeneratedMedia generateImage(String promptText, String negativePrompt, String title, String caption) {
            return new GeneratedMedia("FAKE_IMG".getBytes(StandardCharsets.UTF_8), "image/png", "fake.png", title, caption, caption, caption, "Credit");
        }

        @Override
        public GeneratedMedia generateVideo(String promptText, String negativePrompt, int seconds, String title, String caption) {
            return new GeneratedMedia("FAKE_VID".getBytes(StandardCharsets.UTF_8), "video/mp4", "fake.mp4", title, caption, caption, caption, "Credit");
        }
    }

    private static class FakeKokoroClient extends KokoroClient {
        private final boolean healthy;

        public FakeKokoroClient(boolean healthy) {
            super("http://fake-kokoro:8880");
            this.healthy = healthy;
        }

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public GeneratedMedia synthesizeSpeech(String text, String voice, String title, String caption) {
            return new GeneratedMedia("FAKE_AUD".getBytes(StandardCharsets.UTF_8), "audio/wav", "fake.wav", title, caption, caption, caption, "Credit");
        }
    }

    private static class FakeDatagenWebClient extends DatagenWebClient {
        private final boolean healthy;
        private int idSeq = 1;

        public FakeDatagenWebClient(boolean healthy) {
            super("http://fake-web:8080");
            this.healthy = healthy;
        }

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public String createJournalist(String firstName, String lastName, String email, String bio) {
            return "j-id-" + (idSeq++);
        }

        @Override
        public List<String> listJournalistIds() {
            return List.of("j-id-1", "j-id-2");
        }

        @Override
        public String createArticle(ArticlePayload payload, List<GeneratedMedia> mediaFiles) {
            return "OK";
        }
    }
}
