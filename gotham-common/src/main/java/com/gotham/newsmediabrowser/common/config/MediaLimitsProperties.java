package com.gotham.newsmediabrowser.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Upload limits enforced on {@code /article} media (chosen for local CPU ImageBind).
 *
 * <p>Bound from {@code gotham.media.*}. Defaults (see {@code application.properties}):
 * IMAGE 10 MiB · AUDIO 20 MiB / 5 min · VIDEO 50 MiB / 90 s.
 *
 * @param image IMAGE limits (no duration)
 * @param audio AUDIO limits (size + duration)
 * @param video VIDEO limits (size + duration)
 */
@ConfigurationProperties(prefix = "gotham.media")
public record MediaLimitsProperties(Limit image, Limit audio, Limit video) {

    /**
     * A single media-type limit.
     *
     * @param maxSize     maximum file size (e.g. {@code 10MB})
     * @param maxDuration maximum playback duration (e.g. {@code 5m}); {@code null} for images
     */
    public record Limit(DataSize maxSize, Duration maxDuration) {
    }
}
