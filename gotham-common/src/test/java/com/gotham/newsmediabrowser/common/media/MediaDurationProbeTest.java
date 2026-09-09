package com.gotham.newsmediabrowser.common.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.gotham.newsmediabrowser.common.config.MediaProbeProperties;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the two-tier duration probe. The ffprobe tier itself needs the binary, so it is
 * exercised by {@code MediaDurationProbeFfprobeIT}; these cover the wiring, the output parsing, and
 * the fallback that must keep working on a host with no ffmpeg installed.
 */
class MediaDurationProbeTest {

    private MediaDurationProbe probeWith(boolean enabled, String path) {
        return new MediaDurationProbe(new MediaProbeProperties(enabled, path, Duration.ofSeconds(5)));
    }

    @Test
    void imagesNeverHaveADuration() {
        assertThat(probeWith(true, "ffprobe").probe(MediaType.IMAGE, new byte[] {1, 2, 3}, "x.png")).isEmpty();
    }

    @Test
    void fallsBackToTheContainerParserWhenFfprobeIsMissing() {
        // A binary that cannot exist: the availability check fails and the WAV parser still answers.
        byte[] wav = ContainerDurationParserTest.silentWav(8000, 8000);

        Optional<Duration> duration =
                probeWith(true, "gotham-no-such-ffprobe-binary").probe(MediaType.AUDIO, wav, "clip.wav");

        assertThat(duration).contains(Duration.ofSeconds(1));
    }

    @Test
    void disabledProbeSkipsFfprobeAndStillParsesWav() {
        byte[] wav = ContainerDurationParserTest.silentWav(8000, 16000);

        assertThat(probeWith(false, "ffprobe").probe(MediaType.AUDIO, wav, "clip.wav"))
                .contains(Duration.ofSeconds(2));
    }

    @Test
    void unparsableContainerStaysUnknownRatherThanGuessing() {
        // An unknown duration is what lets GcsStorageService fall through to the size cap.
        assertThat(probeWith(true, "gotham-no-such-ffprobe-binary")
                .probe(MediaType.AUDIO, new byte[] {0, 1, 2, 3}, "clip.mp3"))
                .isEmpty();
    }

    @Test
    void missingBinaryIsReportedUnavailable() {
        assertThat(probeWith(true, "gotham-no-such-ffprobe-binary").ffprobeAvailable()).isFalse();
    }

    @Test
    void parsesFfprobeSecondsOutput() {
        assertThat(MediaDurationProbe.parseSeconds("12.345000\n")).contains(Duration.ofMillis(12345));
        assertThat(MediaDurationProbe.parseSeconds("90")).contains(Duration.ofSeconds(90));
    }

    @Test
    void rejectsFfprobeOutputThatCarriesNoUsableNumber() {
        assertThat(MediaDurationProbe.parseSeconds(null)).isEmpty();
        assertThat(MediaDurationProbe.parseSeconds("")).isEmpty();
        assertThat(MediaDurationProbe.parseSeconds("N/A")).isEmpty();
        assertThat(MediaDurationProbe.parseSeconds("not-a-number")).isEmpty();
        assertThat(MediaDurationProbe.parseSeconds("0")).isEmpty();
        assertThat(MediaDurationProbe.parseSeconds("-4.2")).isEmpty();
    }

    @Test
    void temporaryFileExtensionIsSanitised() {
        assertThat(MediaDurationProbe.extensionOf("clip.mp3")).isEqualTo(".mp3");
        assertThat(MediaDurationProbe.extensionOf("CLIP.MP4")).isEqualTo(".mp4");
        assertThat(MediaDurationProbe.extensionOf(null)).isEqualTo(".bin");
        assertThat(MediaDurationProbe.extensionOf("no-extension")).isEqualTo(".bin");
        assertThat(MediaDurationProbe.extensionOf("trailing.")).isEqualTo(".bin");
        // Nothing from the filename may become a path or an option, even though it only ever names a
        // temp file we create ourselves.
        assertThat(MediaDurationProbe.extensionOf("clip.mp3/../../etc")).isEqualTo(".bin");
        assertThat(MediaDurationProbe.extensionOf("clip. -f lavfi")).isEqualTo(".bin");
        assertThat(MediaDurationProbe.extensionOf("clip.verylongext")).isEqualTo(".bin");
    }
}
