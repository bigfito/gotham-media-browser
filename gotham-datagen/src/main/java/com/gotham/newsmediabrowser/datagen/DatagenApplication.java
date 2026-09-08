package com.gotham.newsmediabrowser.datagen;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * Console entry point for {@code gotham-datagen} — a plain {@code main} (NOT Spring Boot).
 *
 * <p>P10-T01 skeleton: it parses {@code --help} and resolves the run configuration
 * ({@link DatagenConfig}) from properties / system properties / CLI args, then prints it. The actual
 * generation (Qwen text via Ollama, SDXL-Turbo images / Wan video via ComfyUI, Kokoro audio) and the
 * orchestrator that POSTs to {@code /journalist} and {@code /article} arrive in P10-T03…T04, so this
 * command performs no network calls yet.
 *
 * <pre>
 *   java -jar gotham-datagen/target/gotham-datagen.jar --help
 *   java -jar gotham-datagen/target/gotham-datagen.jar --journalists=3 --articles=5 --skip-video
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
     * @return {@code 0} on success or {@code --help}; {@code 2} on a bad argument
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (isHelpRequested(args)) {
            out.println(usage());
            out.println();
            out.println(DatagenConfig.defaultsOnly().describe());
            return 0;
        }

        DatagenConfig config;
        try {
            config = DatagenConfig.load(args);
        } catch (IllegalArgumentException e) {
            err.println("gotham-datagen: " + e.getMessage());
            err.println();
            err.println(usage());
            return 2;
        }

        out.println(config.describe());
        out.println("Skeleton only (P10-T01): generation + HTTP load arrive in P10-T03/T04 — nothing was sent.");
        return 0;
    }

    private static boolean isHelpRequested(String[] args) {
        return args != null && Arrays.stream(args).anyMatch(a -> "--help".equals(a) || "-h".equals(a));
    }

    private static String usage() {
        StringBuilder options = new StringBuilder();
        for (String key : DatagenConfig.keys()) {
            options.append("    --").append(key).append("=<value>\n");
        }
        return """
                Usage: java -jar gotham-datagen.jar [options]

                Generates synthetic journalists + articles and (later phases) loads them through the
                running gotham-web CRUD only. Options override datagen.properties and
                -Dgotham.datagen.* system properties. Boolean flags (skip-*) may be given bare.

                Options:
                %s    --help, -h            show this help and the resolved defaults"""
                .formatted(options.toString());
    }
}
