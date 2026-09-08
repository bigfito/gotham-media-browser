package com.gotham.newsmediabrowser.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link DatagenApplication} CLI (exit codes + output), no JVM spawn. */
class DatagenApplicationTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return DatagenApplication.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void helpExitsZeroAndPrintsUsageAndDefaults() {
        int code = run("--help");

        assertThat(code).isZero();
        assertThat(out()).contains("Usage: java -jar gotham-datagen.jar");
        assertThat(out()).contains("--journalists");
        assertThat(out()).contains("qwen2.5:7b-instruct");
    }

    @Test
    void defaultRunExitsZeroPrintsConfigAndSendsNothing() {
        int code = run();

        assertThat(code).isZero();
        assertThat(out()).contains("gotham-datagen configuration");
        assertThat(out()).contains("nothing was sent");
    }

    @Test
    void badArgumentExitsTwoWithMessage() {
        int code = run("--bogus");

        assertThat(code).isEqualTo(2);
        assertThat(err()).contains("Unknown option");
        assertThat(err()).contains("Usage:");
    }
}
