package com.gotham.newsmediabrowser.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Cloud Storage settings for public media objects.
 *
 * <p>Bound from {@code gotham.gcs.*}. The service-account JSON key itself is NOT here — only the
 * path to the gitignored secret file ({@code secrets/gcp-sa.json}). Real bucket/project values
 * come from the untracked override, not the committed placeholders.
 *
 * @param projectId       GCP project id
 * @param bucket          public bucket name
 * @param credentialsFile path to the service-account JSON key (relative to the run dir)
 */
@ConfigurationProperties(prefix = "gotham.gcs")
public record GcsProperties(String projectId, String bucket, String credentialsFile) {

    /** True when project and bucket look configured (not blank, not a placeholder). */
    public boolean isConfigured() {
        return isReal(projectId) && isReal(bucket);
    }

    private static boolean isReal(String value) {
        return value != null && !value.isBlank() && !value.startsWith("YOUR");
    }
}
