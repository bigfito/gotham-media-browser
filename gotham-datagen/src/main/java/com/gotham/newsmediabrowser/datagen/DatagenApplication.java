package com.gotham.newsmediabrowser.datagen;

import com.gotham.newsmediabrowser.datagen.client.ComfyuiClient;
import com.gotham.newsmediabrowser.datagen.client.DatagenClientException;
import com.gotham.newsmediabrowser.datagen.client.KokoroClient;
import com.gotham.newsmediabrowser.datagen.client.OllamaClient;
import com.gotham.newsmediabrowser.datagen.orchestrator.DatagenOrchestrator;
import com.gotham.newsmediabrowser.datagen.orchestrator.DatagenWebClient;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * Console entry point for {@code gotham-datagen} — a plain {@code main} (NOT Spring Boot).
 *
 * <p>Orchestrates generation of journalists and articles with multimedia (images, audio, videos)
 * and loads them exclusively through the running {@code gotham-web} HTTP CRUD.
 *
 * <pre>
 *   java -jar gotham-datagen/target/gotham-datagen.jar --help
 *   java -jar gotham-datagen/target/gotham-datagen.jar --journalists=15 --articles=25
 *   java -jar gotham-datagen/target/gotham-datagen.jar --dry-run
 * </pre>
 */
public final class DatagenApplication {

    private DatagenApplication() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Runs the CLI and returns the process exit code (testable without spawning a JVM).
     *
     * @return {@code 0} on success or {@code --help}/{@code --dry-run}; {@code 1} on runtime error; {@code 2} on bad argument
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (isHelpRequested(args)) {
            out.println(usage());
            out.println();
            out.println(DatagenConfig.defaultsOnly().describe());
            return 0;
        }

        boolean dryRun = isDryRunRequested(args);
        String[] cleanArgs = cleanArgs(args);

        DatagenConfig config;
        try {
            config = DatagenConfig.load(cleanArgs);
        } catch (IllegalArgumentException e) {
            err.println("gotham-datagen: " + e.getMessage());
            err.println();
            err.println(usage());
            return 2;
        }

        if (dryRun) {
            out.println(config.describe());
            out.println("Dry-run mode: configuration resolved — nothing was sent.");
            return 0;
        }

        try {
            OllamaClient ollama = new OllamaClient(config.ollamaUrl(), config.textModel());
            ComfyuiClient comfyui = new ComfyuiClient(config.comfyuiUrl());
            KokoroClient kokoro = new KokoroClient(config.kokoroUrl());
            DatagenWebClient web = new DatagenWebClient(config.webBaseUrl());

            DatagenOrchestrator orchestrator = new DatagenOrchestrator(config, ollama, comfyui, kokoro, web, out);
            DatagenOrchestrator.DatagenReport report = orchestrator.run();

            return report.errors().isEmpty() ? 0 : 1;
        } catch (DatagenClientException e) {
            err.println("Datagen failed: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private static boolean isHelpRequested(String[] args) {
        return args != null && Arrays.stream(args).anyMatch(a -> "--help".equals(a) || "-h".equals(a));
    }

    private static boolean isDryRunRequested(String[] args) {
        return args != null && Arrays.stream(args).anyMatch("--dry-run"::equals);
    }

    private static String[] cleanArgs(String[] args) {
        if (args == null) return new String[0];
        return Arrays.stream(args).filter(a -> !"--dry-run".equals(a)).toArray(String[]::new);
    }

    private static String usage() {
        StringBuilder options = new StringBuilder();
        for (String key : DatagenConfig.keys()) {
            options.append("    --").append(key).append("=<value>\n");
        }
        return """
                Usage: java -jar gotham-datagen.jar [options]

                Generates synthetic journalists + articles and loads them through the
                running gotham-web CRUD only. Options override datagen.properties and
                -Dgotham.datagen.* system properties. Boolean flags (skip-*) may be given bare.

                Options:
                %s    --dry-run             show resolved configuration without sending HTTP requests
                    --help, -h            show this help and the resolved defaults"""
                .formatted(options.toString());
    }
}
