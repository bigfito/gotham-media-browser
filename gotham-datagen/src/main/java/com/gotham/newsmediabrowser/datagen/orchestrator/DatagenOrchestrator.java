package com.gotham.newsmediabrowser.datagen.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gotham.newsmediabrowser.datagen.DatagenConfig;
import com.gotham.newsmediabrowser.datagen.client.ComfyuiClient;
import com.gotham.newsmediabrowser.datagen.client.DatagenClientException;
import com.gotham.newsmediabrowser.datagen.client.GeneratedMedia;
import com.gotham.newsmediabrowser.datagen.client.KokoroClient;
import com.gotham.newsmediabrowser.datagen.client.OllamaClient;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * End-to-end orchestrator that generates synthetic journalists and articles (with media)
 * and loads them into {@code gotham-web} via HTTP CRUD.
 */
public class DatagenOrchestrator {

    private final DatagenConfig config;
    private final OllamaClient ollamaClient;
    private final ComfyuiClient comfyuiClient;
    private final KokoroClient kokoroClient;
    private final DatagenWebClient webClient;
    private final ObjectMapper objectMapper;
    private final PrintStream out;

    public record DatagenReport(
            int journalistsCreated,
            int articlesCreated,
            int imagesGenerated,
            int audioGenerated,
            int videoGenerated,
            List<String> errors
    ) {}

    public DatagenOrchestrator(
            DatagenConfig config,
            OllamaClient ollamaClient,
            ComfyuiClient comfyuiClient,
            KokoroClient kokoroClient,
            DatagenWebClient webClient,
            PrintStream out
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.ollamaClient = Objects.requireNonNull(ollamaClient, "ollamaClient must not be null");
        this.comfyuiClient = Objects.requireNonNull(comfyuiClient, "comfyuiClient must not be null");
        this.kokoroClient = Objects.requireNonNull(kokoroClient, "kokoroClient must not be null");
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
        this.objectMapper = new ObjectMapper();
        this.out = out != null ? out : System.out;
    }

    public DatagenReport run() {
        out.println("===============================================================================");
        out.println("  Gotham Synthetic Data Generation Orchestrator");
        out.println("===============================================================================");
        out.println(config.describe());

        List<String> errors = new ArrayList<>();

        // 1. Health checks
        out.println("==> Step 1: Checking dependencies health...");
        if (!webClient.isHealthy()) {
            throw new DatagenClientException("GothamWeb", "gotham-web application is not healthy or unreachable at " + config.webBaseUrl());
        }
        out.println("  ✓ gotham-web UP (" + config.webBaseUrl() + ")");

        if (!ollamaClient.isHealthy()) {
            throw new DatagenClientException("Ollama", "Ollama LLM service is not healthy or unreachable at " + config.ollamaUrl());
        }
        out.println("  ✓ Ollama UP (" + config.ollamaUrl() + ")");

        boolean comfyUp = comfyuiClient.isHealthy();
        if (comfyUp) {
            out.println("  ✓ ComfyUI UP (" + config.comfyuiUrl() + ")");
        } else {
            out.println("  ⚠ ComfyUI DOWN (" + config.comfyuiUrl() + ")" + (config.skipImage() && config.skipVideo() ? " [SKIPPED]" : ""));
            if (!config.skipImage() || !config.skipVideo()) {
                throw new DatagenClientException("ComfyUI", "ComfyUI is required for media generation but unreachable. Use --skip-image and/or --skip-video to bypass.");
            }
        }

        boolean kokoroUp = kokoroClient.isHealthy();
        if (kokoroUp) {
            out.println("  ✓ Kokoro TTS UP (" + config.kokoroUrl() + ")");
        } else {
            out.println("  ⚠ Kokoro TTS DOWN (" + config.kokoroUrl() + ")" + (config.skipAudio() ? " [SKIPPED]" : ""));
            if (!config.skipAudio()) {
                throw new DatagenClientException("Kokoro", "Kokoro TTS is required for audio generation but unreachable. Use --skip-audio to bypass.");
            }
        }

        // 2. Generate Journalists
        out.println("\n==> Step 2: Generating " + config.journalists() + " journalists...");
        List<String> journalistIds = new ArrayList<>();

        for (int i = 1; i <= config.journalists(); i++) {
            try {
                JournalistProfile profile = generateJournalistProfile(i);
                String id = webClient.createJournalist(profile.firstName(), profile.lastName(), profile.email(), profile.bio());
                journalistIds.add(id);
                out.println("  + [" + i + "/" + config.journalists() + "] " + profile.firstName() + " " + profile.lastName() + " (" + profile.email() + " -> " + id + ")");
            } catch (Exception e) {
                String err = "Failed to generate/create journalist #" + i + ": " + e.getMessage();
                out.println("  ✗ " + err);
                errors.add(err);
            }
        }

        if (journalistIds.isEmpty()) {
            throw new DatagenClientException("Orchestrator", "No journalists could be created — aborting article generation.");
        }

        // 3. Generate Articles + Media
        out.println("\n==> Step 3: Generating " + config.articles() + " articles...");
        int articlesCreated = 0;
        int imagesGen = 0;
        int audioGen = 0;
        int videoGen = 0;

        for (int i = 1; i <= config.articles(); i++) {
            try {
                // Select 1 or 2 journalists
                String primaryJId = journalistIds.get((i - 1) % journalistIds.size());
                List<String> assignedJournalists = new ArrayList<>();
                assignedJournalists.add(primaryJId);
                if (i % 3 == 0 && journalistIds.size() > 1) {
                    String secondaryJId = journalistIds.get(i % journalistIds.size());
                    if (!secondaryJId.equals(primaryJId)) {
                        assignedJournalists.add(secondaryJId);
                    }
                }

                String status = i == 1 ? "ARCHIVED" : (i % 5 == 0 ? "DRAFT" : "PUBLISHED");
                ArticlePayload payload = generateArticlePayload(i, status, assignedJournalists);

                // Generate media for article
                List<GeneratedMedia> mediaFiles = new ArrayList<>();

                // Images
                if (!config.skipImage() && config.imagesPerArticle() > 0) {
                    for (int m = 1; m <= config.imagesPerArticle(); m++) {
                        String imgPrompt = payload.imagePrompt() + ", shot " + m + ", photorealistic news photography";
                        GeneratedMedia img = comfyuiClient.generateImage(imgPrompt, "blurry, low quality", payload.title() + " (Photo " + m + ")", payload.summary());
                        mediaFiles.add(img);
                        imagesGen++;
                    }
                }

                // Audio (TTS)
                if (!config.skipAudio() && config.audioPerArticle() > 0) {
                    for (int a = 1; a <= config.audioPerArticle(); a++) {
                        String script = a == 1 ? payload.summary() : payload.title() + ". " + payload.summary();
                        GeneratedMedia aud = kokoroClient.synthesizeSpeech(script, "af_heart", payload.title() + " (Audio Report " + a + ")", "Broadcast voice dispatch");
                        mediaFiles.add(aud);
                        audioGen++;
                    }
                }

                // Videos
                if (!config.skipVideo() && config.videoPerArticle() > 0) {
                    for (int v = 1; v <= config.videoPerArticle(); v++) {
                        String vidPrompt = payload.videoPrompt() + ", clip " + v + ", 5 seconds news broadcast";
                        GeneratedMedia vid = comfyuiClient.generateVideo(vidPrompt, "blurry, static", config.videoSeconds(), payload.title() + " (Video Clip " + v + ")", "News broadcast archive");
                        mediaFiles.add(vid);
                        videoGen++;
                    }
                }

                webClient.createArticle(payload, mediaFiles);
                articlesCreated++;
                out.println("  + [" + i + "/" + config.articles() + "] " + payload.title() + " [" + payload.section() + "/" + payload.status() + ", " + mediaFiles.size() + " media]");
            } catch (Exception e) {
                String err = "Failed to generate/create article #" + i + ": " + e.getMessage();
                out.println("  ✗ " + err);
                errors.add(err);
            }
        }

        // 4. Summary report
        out.println("\n===============================================================================");
        out.println("  Datagen Run Complete");
        out.println("===============================================================================");
        out.println("  Journalists created: " + journalistIds.size() + " / " + config.journalists());
        out.println("  Articles created   : " + articlesCreated + " / " + config.articles());
        out.println("  Images generated   : " + imagesGen);
        out.println("  Audio generated    : " + audioGen);
        out.println("  Video generated    : " + videoGen);
        out.println("  Total media assets : " + (imagesGen + audioGen + videoGen));
        out.println("  Errors encountered : " + errors.size());
        out.println("===============================================================================\n");

        return new DatagenReport(journalistIds.size(), articlesCreated, imagesGen, audioGen, videoGen, errors);
    }

    private JournalistProfile generateJournalistProfile(int index) {
        String sysPrompt = "You are a database seeding generator for Gotham City news media. Generate a JSON object for a journalist.";
        String userPrompt = "Generate a JSON object with fields: firstName (string), lastName (string), email (unique string @gothamgazette.com), bio (1-2 sentences). Return ONLY valid JSON.";

        try {
            String jsonStr = ollamaClient.generateJson(sysPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(jsonStr);
            String first = root.path("firstName").asText("Reporter" + index);
            String last = root.path("lastName").asText("Gotham");
            String email = root.path("email").asText("reporter" + index + "_" + UUID.randomUUID().toString().substring(0, 4) + "@gothamgazette.com");
            String bio = root.path("bio").asText("Senior investigative reporter covering Gotham City affairs.");

            return new JournalistProfile(first, last, email, bio);
        } catch (Exception e) {
            // Fallback to deterministic Gotham names if LLM formatting fails
            String[] firstNames = {"Vicki", "Alexander", "Lois", "Clark", "Jack", "Chase", "Harvey", "Rachel", "Gillian", "Michael", "Sarah", "Celia", "Sal", "Carmine", "Bruce"};
            String[] lastNames = {"Vale", "Knox", "Lane", "Kent", "Ryder", "Meridian", "Dent", "Dawes", "Loeb", "Akins", "Essen", "Forest", "Maroni", "Falcone", "Wayne"};

            String first = firstNames[(index - 1) % firstNames.length];
            String last = lastNames[(index - 1) % lastNames.length];
            String email = first.toLowerCase() + "." + last.toLowerCase() + index + "@gothamgazette.com";
            String bio = "Investigative journalist at Gotham Gazette covering civic governance and metropolitan crime.";

            return new JournalistProfile(first, last, email, bio);
        }
    }

    private ArticlePayload generateArticlePayload(int index, String status, List<String> journalistIds) {
        String[] sections = {"Politics", "Crime", "Business", "Culture", "Gotham Life", "Metropolis", "Science", "Opinion"};
        String section = sections[(index - 1) % sections.length];

        String sysPrompt = "You are an investigative journalist writing a news story for Gotham Gazette in the section " + section + ". Generate a JSON object.";
        String userPrompt = "Generate a JSON object with: title (string), subtitle (string), summary (2 sentences), body (3 paragraphs), tags (comma-separated string), location (Gotham location), imagePrompt (T2I prompt for photography), audioScript (broadcast transcript), videoPrompt (T2V prompt for 5s video). Return ONLY valid JSON.";

        try {
            String jsonStr = ollamaClient.generateJson(sysPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(jsonStr);

            String title = root.path("title").asText("Gotham City Council Passes Landmark Transit Reform");
            String subtitle = root.path("subtitle").asText("Major funding allocated to Monorail and subway upgrades.");
            String summary = root.path("summary").asText("The Gotham City Council voted overwhelmingly today to approve new funding for metropolitan public transit.");
            String body = root.path("body").asText("In a pivotal session at Gotham City Hall, council members addressed long-standing transit delays across downtown districts. The multi-million dollar package aims to modernize signal systems and expand coverage to the Narrows and Bowery.\n\nOpposition leaders voiced caution over fiscal oversight, but civic groups praised the initiative as a major step forward for urban mobility.");
            String tags = root.path("tags").asText("transit,city-hall,gotham-council,urban-planning");
            String location = root.path("location").asText("Gotham City Hall");
            String source = "Gotham Gazette";
            String imagePrompt = root.path("imagePrompt").asText("Gotham City Hall council chambers press conference, news photo");
            String audioScript = root.path("audioScript").asText(summary);
            String videoPrompt = root.path("videoPrompt").asText("Gotham City subway arriving at station with commuters, 5 seconds video");

            return new ArticlePayload(
                    title, subtitle, summary, body, status, "en", section, tags, location, source,
                    title, summary, tags, journalistIds, imagePrompt, audioScript, videoPrompt
            );
        } catch (Exception e) {
            // Structured fallback
            String title = "Gotham " + section + " Report #" + index + ": Metropolitan Developments";
            String summary = "Comprehensive coverage of current events and community impacts across Gotham City's " + section.toLowerCase() + " sector.";
            String body = "Civic leaders and local organizations met this week to evaluate emerging trends across Gotham City.\n\nKey stakeholders emphasized the need for transparency, sustainable investment, and community engagement to ensure long-term stability.\n\nFurther updates will be published as official findings are released.";
            String tags = section.toLowerCase() + ",gotham,news,civic-affairs";
            String imagePrompt = "Gotham City street scene in " + section + " district, documentary photo";
            String videoPrompt = "Gotham City skyline with moving clouds and street traffic, 5 seconds";

            return new ArticlePayload(
                    title, "Developments in " + section, summary, body, status, "en", section, tags, "Gotham City", "Gotham Gazette",
                    title, summary, tags, journalistIds, imagePrompt, summary, videoPrompt
            );
        }
    }

    private record JournalistProfile(String firstName, String lastName, String email, String bio) {}
}
