package com.gotham.newsmediabrowser.datagen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Resolved configuration for a {@code gotham-datagen} run.
 *
 * <p>Values are layered, later layers winning:
 * <ol>
 *   <li>the built-in defaults (the locked prototype volumes: 15 / 25 / 5+5+5 / 5&nbsp;s),</li>
 *   <li>an optional {@code datagen.properties} on the classpath,</li>
 *   <li>system properties prefixed {@code gotham.datagen.} (e.g. {@code -Dgotham.datagen.journalists=3}),</li>
 *   <li>CLI arguments {@code --key=value} / {@code --skip-image} (see {@link DatagenApplication}).</li>
 * </ol>
 * Keys are kebab-case, e.g. {@code web-base-url}, {@code images-per-article}, {@code skip-video}.
 *
 * @param webBaseUrl       base URL of the running {@code gotham-web} (all loads go through its CRUD)
 * @param ollamaUrl        Ollama endpoint (Qwen text generation) — used from P10-T03
 * @param comfyuiUrl       ComfyUI endpoint (SDXL-Turbo image / Wan video) — used from P10-T03
 * @param kokoroUrl        Kokoro endpoint (TTS audio) — used from P10-T03
 * @param textModel        Ollama model id (text is required; images/audio/video are skippable)
 * @param journalists      how many journalists to generate
 * @param articles         how many articles to generate
 * @param imagesPerArticle IMAGE assets per article
 * @param audioPerArticle  AUDIO assets per article
 * @param videoPerArticle  VIDEO assets per article
 * @param videoSeconds     length of each generated video clip, in seconds
 * @param skipImage        skip IMAGE generation when the helper is unavailable
 * @param skipAudio        skip AUDIO generation when the helper is unavailable
 * @param skipVideo        skip VIDEO generation when the helper is unavailable
 */
public record DatagenConfig(
        String webBaseUrl,
        String ollamaUrl,
        String comfyuiUrl,
        String kokoroUrl,
        String textModel,
        int journalists,
        int articles,
        int imagesPerArticle,
        int audioPerArticle,
        int videoPerArticle,
        int videoSeconds,
        boolean skipImage,
        boolean skipAudio,
        boolean skipVideo) {

    /** Property-file / system-property prefix and the classpath file name. */
    public static final String PREFIX = "gotham.datagen.";
    private static final String PROPERTIES_RESOURCE = "datagen.properties";

    private static final Map<String, String> DEFAULTS = defaults();

    private static Map<String, String> defaults() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("web-base-url", "http://localhost:8080");
        d.put("ollama-url", "http://localhost:11434");
        d.put("comfyui-url", "http://localhost:8188");
        d.put("kokoro-url", "http://localhost:8880");
        d.put("text-model", "qwen2.5:7b-instruct");
        d.put("journalists", "15");
        d.put("articles", "25");
        d.put("images-per-article", "5");
        d.put("audio-per-article", "5");
        d.put("video-per-article", "5");
        d.put("video-seconds", "5");
        d.put("skip-image", "false");
        d.put("skip-audio", "false");
        d.put("skip-video", "false");
        return Map.copyOf(d);
    }

    /** The kebab-case keys this config understands (also the CLI flag names). */
    public static Iterable<String> keys() {
        return DEFAULTS.keySet();
    }

    /**
     * Resolves the configuration from the layered sources.
     *
     * @param args raw CLI arguments (already stripped of {@code --help}); unknown flags are rejected
     * @throws IllegalArgumentException if a flag is unknown or a numeric value is invalid
     */
    public static DatagenConfig load(String[] args) {
        Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
        overlayClasspathProperties(values);
        overlaySystemProperties(values);
        overlayArgs(values, args);

        return new DatagenConfig(
                values.get("web-base-url"),
                values.get("ollama-url"),
                values.get("comfyui-url"),
                values.get("kokoro-url"),
                values.get("text-model"),
                intValue(values, "journalists"),
                intValue(values, "articles"),
                intValue(values, "images-per-article"),
                intValue(values, "audio-per-article"),
                intValue(values, "video-per-article"),
                intValue(values, "video-seconds"),
                boolValue(values, "skip-image"),
                boolValue(values, "skip-audio"),
                boolValue(values, "skip-video"));
    }

    /** Built-in defaults with no overlays (handy for tests and {@code --help}). */
    public static DatagenConfig defaultsOnly() {
        return load(new String[0]);
    }

    private static void overlayClasspathProperties(Map<String, String> values) {
        try (InputStream in = DatagenConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (in == null) {
                return;
            }
            Properties props = new Properties();
            props.load(in);
            for (String key : DEFAULTS.keySet()) {
                String value = props.getProperty(PREFIX + key);
                if (value != null && !value.isBlank()) {
                    values.put(key, value.strip());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + PROPERTIES_RESOURCE, e);
        }
    }

    private static void overlaySystemProperties(Map<String, String> values) {
        for (String key : DEFAULTS.keySet()) {
            String value = System.getProperty(PREFIX + key);
            if (value != null && !value.isBlank()) {
                values.put(key, value.strip());
            }
        }
    }

    private static void overlayArgs(Map<String, String> values, String[] args) {
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument '" + arg + "' (expected --key=value).");
            }
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            String key = eq >= 0 ? body.substring(0, eq) : body;
            String value = eq >= 0 ? body.substring(eq + 1) : "true"; // bare --skip-image == true
            if (!DEFAULTS.containsKey(key)) {
                throw new IllegalArgumentException("Unknown option '--" + key + "'. Known: " + DEFAULTS.keySet());
            }
            values.put(key, value.strip());
        }
    }

    private static int intValue(Map<String, String> values, String key) {
        String raw = values.get(key);
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed < 0) {
                throw new IllegalArgumentException("Option '" + key + "' must be >= 0 but was " + parsed + ".");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Option '" + key + "' must be an integer but was '" + raw + "'.", e);
        }
    }

    private static boolean boolValue(Map<String, String> values, String key) {
        return Boolean.parseBoolean(values.get(key));
    }

    /** Total media assets this run would create ({@code articles * (image + audio + video per article)}). */
    public int totalMediaAssets() {
        int perArticle = (skipImage ? 0 : imagesPerArticle)
                + (skipAudio ? 0 : audioPerArticle)
                + (skipVideo ? 0 : videoPerArticle);
        return articles * perArticle;
    }

    /** Human-readable multi-line summary for {@code --help} / run banners. */
    public String describe() {
        return """
                gotham-datagen configuration
                  web-base-url        : %s
                  ollama-url          : %s   (text — Qwen, required)
                  comfyui-url         : %s   (image SDXL-Turbo / video Wan)
                  kokoro-url          : %s   (audio TTS)
                  text-model          : %s
                  journalists         : %d
                  articles            : %d
                  images-per-article  : %d%s
                  audio-per-article   : %d%s
                  video-per-article   : %d%s   (%d s each)
                  total media assets  : %d
                """.formatted(
                webBaseUrl, ollamaUrl, comfyuiUrl, kokoroUrl, textModel,
                journalists, articles,
                imagesPerArticle, skipImage ? "   (SKIPPED)" : "",
                audioPerArticle, skipAudio ? "   (SKIPPED)" : "",
                videoPerArticle, skipVideo ? "   (SKIPPED)" : "", videoSeconds,
                totalMediaAssets());
    }
}
