package com.gotham.newsmediabrowser.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the app measures playback duration before enforcing the upload time caps.
 *
 * <p>Bound from {@code gotham.media.probe.*}. The real measurement is an {@code ffprobe} subprocess;
 * when the binary is missing or disabled the app falls back to the pure-Java container parsers
 * ({@code ContainerDurationParser}, WAV/AIFF/AU and MP4/MOV only).
 *
 * <p><strong>Binding note:</strong> constructor binding gives a missing {@code boolean} the value
 * {@code false}, so {@code gotham.media.probe.enabled=true} is set explicitly in
 * {@code application.properties}. Deleting that line silently downgrades the app to the fallback
 * parsers — it will still work, just measure fewer formats.
 *
 * @param enabled  run {@code ffprobe} at all ({@code false} = pure-Java parsers only)
 * @param path     executable to run; a bare name is resolved on {@code PATH}
 * @param timeout  how long one probe may take before the subprocess is killed
 */
@ConfigurationProperties(prefix = "gotham.media.probe")
public record MediaProbeProperties(boolean enabled, String path, Duration timeout) {

    public MediaProbeProperties {
        if (path == null || path.isBlank()) {
            path = "ffprobe";
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(10);
        }
    }

    /** Defaults used when nothing is configured: ffprobe on, resolved from {@code PATH}. */
    public static MediaProbeProperties defaults() {
        return new MediaProbeProperties(true, "ffprobe", Duration.ofSeconds(10));
    }
}
