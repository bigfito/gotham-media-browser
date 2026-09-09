package com.gotham.newsmediabrowser.common.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.gotham.newsmediabrowser.common.config.MediaProbeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Live check that the ffprobe tier really measures the containers the pure-Java fallback cannot read.
 * Skipped (not failed) when ffmpeg is not installed, so it never blocks {@code mvn test} or CI.
 *
 * <p>ffmpeg is used here to synthesise the fixtures too — generating a 3-second MP3 rather than
 * committing binary test assets.
 *
 * <pre>
 * mvn -pl gotham-common test -Dtest=MediaDurationProbeFfprobeIT -DfailIfNoTests=false
 * </pre>
 */
@Tag("integration")
class MediaDurationProbeFfprobeIT {

    private static final Duration TOLERANCE = Duration.ofMillis(250);

    private MediaDurationProbe probe() {
        return new MediaDurationProbe(new MediaProbeProperties(true, "ffprobe", Duration.ofSeconds(20)));
    }

    @Test
    void measuresAnMp3ThatTheContainerParserCannotRead() throws Exception {
        assumeTrue(probe().ffprobeAvailable(), "ffprobe not installed");

        byte[] mp3 = synthesise("mp3", 3);
        // The whole point of the ffprobe tier: this format is invisible to the JDK parsers.
        assertThat(ContainerDurationParser.probe(MediaType.AUDIO, mp3)).isEmpty();

        Optional<Duration> measured = probe().probe(MediaType.AUDIO, mp3, "clip.mp3");

        assertThat(measured).isPresent();
        assertThat(measured.get()).isCloseTo(Duration.ofSeconds(3), TOLERANCE);
    }

    @Test
    void measuresAWebmVideo() throws Exception {
        assumeTrue(probe().ffprobeAvailable(), "ffprobe not installed");

        byte[] webm = synthesise("webm", 2);

        Optional<Duration> measured = probe().probe(MediaType.VIDEO, webm, "clip.webm");

        assertThat(measured).isPresent();
        assertThat(measured.get()).isCloseTo(Duration.ofSeconds(2), TOLERANCE);
    }

    @Test
    void garbageBytesStayUnknownSoTheSizeCapDecides() {
        assumeTrue(probe().ffprobeAvailable(), "ffprobe not installed");

        assertThat(probe().probe(MediaType.AUDIO, "not media at all".getBytes(), "clip.mp3")).isEmpty();
    }

    @TempDir
    Path workDir;

    /** Renders {@code seconds} of silence/blank video into the given container with ffmpeg. */
    private byte[] synthesise(String extension, int seconds) throws IOException, InterruptedException {
        Path out = workDir.resolve("fixture." + extension);
        List<String> command = "webm".equals(extension)
                ? List.of("ffmpeg", "-v", "error", "-y",
                        "-f", "lavfi", "-i", "color=c=black:s=160x120:r=10:d=" + seconds,
                        out.toString())
                : List.of("ffmpeg", "-v", "error", "-y",
                        "-f", "lavfi", "-i", "anullsrc=r=8000:cl=mono:d=" + seconds,
                        out.toString());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        assumeTrue(process.waitFor() == 0, "ffmpeg could not build the " + extension + " fixture: " + output);
        return Files.readAllBytes(out);
    }
}
