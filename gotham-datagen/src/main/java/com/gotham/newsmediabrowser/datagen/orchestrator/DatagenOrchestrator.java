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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    private static final List<JournalistProfile> NEWSROOM = List.of(
            new JournalistProfile("Vicki", "Vale", "vicki.vale@gothamgazette.example",
                    "Investigative reporter covering city hall, Wayne Enterprises, and Gotham politics."),
            new JournalistProfile("Alexander", "Knox", "alex.knox@gothamgazette.example",
                    "City desk veteran on the crime, courts, and corruption beat."),
            new JournalistProfile("Summer", "Gleeson", "summer.gleeson@gothamgazette.example",
                    "Arts and culture critic covering museums, film, and the Gotham music scene."),
            new JournalistProfile("Jack", "Ryder", "jack.ryder@gothamgazette.example",
                    "Business and economy columnist tracking Gotham's startup district."),
            new JournalistProfile("Lois", "Lane", "lois.lane@gothamgazette.example",
                    "Lead investigative correspondent on public safety and civic oversight."),
            new JournalistProfile("Clark", "Kent", "clark.kent@gothamgazette.example",
                    "Metro reporter covering neighborhoods, transit, and daily life in Gotham."),
            new JournalistProfile("Sarah", "Essen", "sarah.essen@gothamgazette.example",
                    "Science and infrastructure writer focused on energy, health, and urban systems."),
            new JournalistProfile("Chase", "Meridian", "chase.meridian@gothamgazette.example",
                    "Opinion editor writing on justice, reform, and the city's public conscience."),
            new JournalistProfile("Monique", "Hill", "monique.hill@gothamgazette.example",
                    "Culture desk reporter on festivals, food, and Gotham nightlife."),
            new JournalistProfile("Harvey", "Bullock", "harvey.bullock@gothamgazette.example",
                    "Courts reporter following trials, precinct politics, and police reform."),
            new JournalistProfile("Barbara", "Gordon", "barbara.gordon@gothamgazette.example",
                    "Technology correspondent covering civic data, surveillance, and research labs."),
            new JournalistProfile("Lucius", "Fox", "lucius.fox@gothamgazette.example",
                    "Business reporter specializing in manufacturing, energy, and Wayne Enterprises."),
            new JournalistProfile("Leslie", "Thompkins", "leslie.thompkins@gothamgazette.example",
                    "Public-health reporter covering clinics, hospitals, and East End outreach."),
            new JournalistProfile("Renee", "Montoya", "renee.montoya@gothamgazette.example",
                    "Crime reporter embedded with the major-case squad and city prosecutors."),
            new JournalistProfile("Tamara", "Soong", "tamara.soong@gothamgazette.example",
                    "Metropolis bureau chief covering cross-city policy and regional commerce.")
    );

    private static final List<StorySeed> STORY_SEEDS = List.of(
            new StorySeed("Politics", "City Council Approves Transit Funding Expansion",
                    "Gotham City Hall", "council vote on light-rail and late-night bus funding"),
            new StorySeed("Crime", "GCPD Overtime Scandal Reaches Internal Affairs",
                    "GCPD Headquarters", "precinct overtime records and an internal-affairs inquiry"),
            new StorySeed("Business", "Wayne Enterprises Unveils Clean-Energy Microgrid",
                    "Wayne Tower", "downtown renewable microgrid pilot and blackout risk"),
            new StorySeed("Culture", "Gotham Museum Reopens Its Restored Grand Wing",
                    "Gotham Museum", "restored murals and a new photography gallery"),
            new StorySeed("Gotham Life", "East End Night Market Draws Record Crowds",
                    "The Narrows", "street-food night market and neighborhood recovery"),
            new StorySeed("Metropolis", "Gotham-Metropolis Rail Link Clears Environmental Review",
                    "Gotham Central Station", "intercity rail link and commuting times"),
            new StorySeed("Science", "University Lab Maps Gotham Harbor Microplastics",
                    "Gotham University", "harbor water sampling and public-health findings"),
            new StorySeed("Opinion", "The Case for Independent Oversight of Arkham",
                    "Arkham Island", "civilian review of Arkham admissions and transfers"),
            new StorySeed("Politics", "Mayor's Housing Bond Faces Narrow Council Test",
                    "Gotham City Hall", "affordable-housing bond and East End displacement"),
            new StorySeed("Crime", "Iceberg Lounge Raid Yields Stolen Art Cache",
                    "Iceberg Lounge", "raid recovering museum pieces and club finances"),
            new StorySeed("Business", "Startup District Posts Record Venture Quarter",
                    "The Bowery", "fintech and clean-tech hiring in the startup district"),
            new StorySeed("Culture", "Old Opera House Stages a Sold-Out Revival",
                    "Gotham Opera House", "opening night of a restored opera production")
    );

    private JournalistProfile generateJournalistProfile(int index) {
        JournalistProfile assigned = NEWSROOM.get((index - 1) % NEWSROOM.size());
        String sysPrompt = "You write staff biographies for the Gotham Gazette. Return JSON only.";
        String userPrompt = "Write a 2-sentence newsroom bio for " + assigned.firstName() + " "
                + assigned.lastName() + ", email " + assigned.email()
                + ". Stay consistent with this beat: " + assigned.bio()
                + ". JSON keys: firstName, lastName, email, bio. Do not invent a different person.";
        try {
            String jsonStr = ollamaClient.generateJson(sysPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(jsonStr);
            String bio = root.path("bio").asText(assigned.bio());
            if (bio == null || bio.isBlank()) {
                bio = assigned.bio();
            }
            return new JournalistProfile(assigned.firstName(), assigned.lastName(), assigned.email(), bio);
        } catch (Exception e) {
            return assigned;
        }
    }

    private ArticlePayload generateArticlePayload(int index, String status, List<String> journalistIds) {
        StorySeed seed = STORY_SEEDS.get((index - 1) % STORY_SEEDS.size());
        String publishedAt = LocalDateTime.of(2026, 8, 1, 8, 0)
                .plusDays(index)
                .plusHours(index % 6)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        String slug = ArticlePayload.slugify(seed.title()) + "-" + index;
        String canonicalUrl = "https://www.gothamgazette.example/articles/" + slug;

        String sysPrompt = "You are a Gotham Gazette reporter in the " + seed.section()
                + " section. Write one specific news story. Return JSON only.";
        String userPrompt = "Story assignment #" + index + ": " + seed.assignment()
                + " at " + seed.location()
                + ". Suggested headline: " + seed.title()
                + ". JSON keys: title, subtitle, summary (2 sentences), body (one string, 3 paragraphs "
                + "separated by \\n\\n — not a JSON array), tags (comma-separated string), location, "
                + "seoTitle, seoDescription, seoKeywords, imagePrompt, audioScript, videoPrompt. "
                + "Keep names, places, and facts inside Gotham. Do not invent a different topic.";

        try {
            String jsonStr = ollamaClient.generateJson(sysPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(jsonStr);
            String title = jsonText(root.get("title"), seed.title());
            String subtitle = jsonText(root.get("subtitle"), seed.assignment());
            String summary = jsonText(root.get("summary"),
                    "Gotham Gazette reports from " + seed.location() + " on " + seed.assignment() + ".");
            String body = jsonText(root.get("body"), fallbackBody(seed, summary));
            String tags = jsonText(root.get("tags"), seed.section().toLowerCase() + ",gotham," + slug);
            String location = jsonText(root.get("location"), seed.location());
            String seoTitle = jsonText(root.get("seoTitle"), title + " | Gotham Gazette");
            String seoDescription = jsonText(root.get("seoDescription"), summary);
            String seoKeywords = jsonText(root.get("seoKeywords"), tags);
            String imagePrompt = nonBlank(root.path("imagePrompt").asText(),
                    seed.location() + ", documentary news photograph, " + seed.assignment());
            String audioScript = nonBlank(root.path("audioScript").asText(), summary);
            String videoPrompt = nonBlank(root.path("videoPrompt").asText(),
                    seed.location() + ", 5 second news establishing shot");

            return new ArticlePayload(
                    title, subtitle, summary, body, status, "en", seed.section(), tags, location, "Gotham Gazette",
                    seoTitle, seoDescription, seoKeywords, slug, publishedAt, canonicalUrl,
                    journalistIds, imagePrompt, audioScript, videoPrompt
            );
        } catch (Exception e) {
            return new ArticlePayload(
                    seed.title(), seed.assignment(),
                    "Gotham Gazette reports from " + seed.location() + " on " + seed.assignment() + ".",
                    fallbackBody(seed, null), status, "en", seed.section(),
                    seed.section().toLowerCase() + ",gotham,news", seed.location(), "Gotham Gazette",
                    seed.title() + " | Gotham Gazette",
                    "Coverage of " + seed.assignment() + " from " + seed.location() + ".",
                    seed.section().toLowerCase() + ",gotham",
                    slug, publishedAt, canonicalUrl, journalistIds,
                    seed.location() + ", documentary news photograph",
                    "This is a Gotham Gazette voice report from " + seed.location() + " on " + seed.assignment() + ".",
                    seed.location() + ", 5 second news establishing shot"
            );
        }
    }

    private static String nonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value.strip() : fallback;
    }

    /**
     * Coerce a JSON value to copy text. Qwen often returns {@code body} or {@code tags} as an
     * array of strings; {@link JsonNode#asText()} is empty for arrays, which previously dropped
     * the generated copy.
     */
    private static String jsonText(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return nonBlank(node.asText(), fallback);
        }
        if (node.isArray()) {
            StringBuilder joined = new StringBuilder();
            String separator = guessJoin(node);
            for (JsonNode item : node) {
                String part = jsonText(item, null);
                if (part == null || part.isBlank()) {
                    continue;
                }
                if (joined.length() > 0) {
                    joined.append(separator);
                }
                joined.append(part.strip());
            }
            return joined.length() > 0 ? joined.toString() : fallback;
        }
        if (node.isObject()) {
            for (String key : List.of("text", "paragraph", "content", "value")) {
                if (node.has(key)) {
                    String nested = jsonText(node.get(key), null);
                    if (nested != null && !nested.isBlank()) {
                        return nested;
                    }
                }
            }
        }
        return fallback;
    }

    private static String guessJoin(JsonNode array) {
        int textual = 0;
        int maxLen = 0;
        for (JsonNode item : array) {
            if (item != null && item.isTextual()) {
                textual++;
                maxLen = Math.max(maxLen, item.asText().length());
            }
        }
        return textual >= 2 && maxLen > 40 ? "\n\n" : ", ";
    }

    private static String fallbackBody(StorySeed seed, String summary) {
        String lead = nonBlank(summary,
                "Gotham Gazette reports from " + seed.location() + " on " + seed.assignment() + ".");
        return lead + "\n\n"
                + "Gazette staff reporting from " + seed.location()
                + " reconstructed " + seed.assignment()
                + " from official records, on-the-record interviews, and neighborhood accounts.\n\n"
                + "City officials said the next public steps will be announced from "
                + seed.location() + ". This newsroom will continue to follow the story.";
    }

    private record JournalistProfile(String firstName, String lastName, String email, String bio) {}

    private record StorySeed(String section, String title, String location, String assignment) {}
}
