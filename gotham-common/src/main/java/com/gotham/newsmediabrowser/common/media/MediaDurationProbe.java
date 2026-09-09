package com.gotham.newsmediabrowser.common.media;

import com.gotham.newsmediabrowser.common.config.MediaProbeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Measures the playback duration of an uploaded asset so {@link GcsStorageService} can enforce the
 * per-type time caps.
 *
 * <p>Two tiers, in order:
 *
 * <ol>
 *   <li><strong>{@code ffprobe}</strong> — the real measurement. Handles every container the demo can
 *       receive (MP3, OGG, FLAC, WebM, MOV, MKV), not just the two the JDK can parse. Run as a
 *       subprocess over a temp file, with a hard timeout and the process destroyed on the way out.</li>
 *   <li><strong>{@link ContainerDurationParser}</strong> — pure-Java fallback for when ffprobe is
 *       absent or disabled. WAV/AIFF/AU and MP4/MOV only.</li>
 * </ol>
 *
 * <p>An empty result still means "unknown", and an unknown duration is <em>not</em> a rejection: the
 * caller stores the object on its size cap alone. ffprobe makes unknown rare; it does not make the
 * cap mandatory, because the binary is not guaranteed to be on every host that runs this app.
 *
 * <p>The subprocess is built from an argument list (never a shell string), and its only variable
 * argument is a temp file this class created — nothing user-supplied reaches the command line.
 */
@Component
public class MediaDurationProbe {

    private static final Logger log = LoggerFactory.getLogger(MediaDurationProbe.class);

    private final MediaProbeProperties properties;
    /** Tri-state cache of the availability check: null = not probed yet. */
    private volatile Boolean ffprobeAvailable;

    public MediaDurationProbe(MediaProbeProperties properties) {
        this.properties = properties != null ? properties : MediaProbeProperties.defaults();
    }

    /**
     * @param type             media kind; IMAGE always returns empty (no duration)
     * @param data             the uploaded bytes
     * @param originalFilename used only for the temp file extension, which helps ffprobe pick a
     *                         demuxer for containers it cannot sniff; may be {@code null}
     * @return the measured duration, or empty when it could not be determined
     */
    public Optional<Duration> probe(MediaType type, byte[] data, String originalFilename) {
        if (type == null || type == MediaType.IMAGE || data == null || data.length == 0) {
            return Optional.empty();
        }
        if (properties.enabled() && ffprobeAvailable()) {
            Optional<Duration> measured = runFfprobe(data, originalFilename);
            if (measured.isPresent()) {
                return measured;
            }
            log.debug("ffprobe reported no duration for {} ({} bytes); falling back to the container parser",
                    type, data.length);
        }
        return ContainerDurationParser.probe(type, data);
    }

    /** True once {@code <path> -version} has run successfully; the result is cached for the JVM. */
    boolean ffprobeAvailable() {
        Boolean cached = ffprobeAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (ffprobeAvailable == null) {
                ffprobeAvailable = execute(List.of(properties.path(), "-version")).isPresent();
                if (!ffprobeAvailable) {
                    log.info("ffprobe ({}) is not available; duration checks fall back to the WAV/MP4 "
                            + "parser and other containers upload on their size cap alone.", properties.path());
                }
            }
            return ffprobeAvailable;
        }
    }

    private Optional<Duration> runFfprobe(byte[] data, String originalFilename) {
        Path temp = null;
        try {
            temp = Files.createTempFile("gotham-probe-", extensionOf(originalFilename));
            Files.write(temp, data);
            return execute(List.of(
                    properties.path(),
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    temp.toAbsolutePath().toString()))
                    .flatMap(MediaDurationProbe::parseSeconds);
        } catch (IOException e) {
            log.warn("Could not stage the upload for ffprobe: {}", e.toString());
            return Optional.empty();
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * Runs the command and returns its stdout, or empty on a non-zero exit, a timeout, or a missing
     * binary.
     *
     * <p>Three things keep a stuck subprocess from becoming a stuck request thread: stdin is closed so
     * ffprobe can never wait on input, stderr is discarded by the OS so a file that provokes pages of
     * diagnostics cannot fill an undrained pipe and block the writer, and stdout is fully drained
     * before {@code waitFor}. The process is destroyed on every exit path.
     */
    private Optional<String> execute(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.getOutputStream().close();
            String stdout;
            try (InputStream in = process.getInputStream()) {
                stdout = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("ffprobe timed out after {}s", properties.timeout().toSeconds());
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                log.debug("ffprobe exited {} for command {}", process.exitValue(), command.get(0));
                return Optional.empty();
            }
            return Optional.of(stdout);
        } catch (IOException e) {
            log.debug("Could not run {}: {}", command.get(0), e.toString());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while waiting for ffprobe");
            return Optional.empty();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** Parses the ffprobe {@code format=duration} output (seconds as a decimal, or {@code N/A}). */
    static Optional<Duration> parseSeconds(String output) {
        if (output == null) {
            return Optional.empty();
        }
        String value = output.strip();
        if (value.isEmpty() || "n/a".equals(value.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        try {
            double seconds = Double.parseDouble(value);
            if (!Double.isFinite(seconds) || seconds <= 0) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofMillis(Math.round(seconds * 1000.0)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Lower-cased dot-extension of the upload, or {@code .bin}; never anything but [a-z0-9]. */
    static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return ".bin";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return ".bin";
        }
        String extension = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (extension.length() > 5 || !extension.matches("[a-z0-9]+")) {
            return ".bin";
        }
        return "." + extension;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("Could not delete probe temp file {}: {}", path, e.toString());
        }
    }
}
