package com.gotham.newsmediabrowser.common.config;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Google Cloud Storage {@link Storage} client from {@link GcsProperties}.
 *
 * <p>Credentials are loaded with intent:
 * <ul>
 *   <li><b>Secret file present</b> → use it. If it exists but cannot be read/parsed, fail fast with
 *       {@code CRED-001} — a misconfigured secret must not be silently ignored.</li>
 *   <li><b>Secret file absent</b> → fall back to the environment's Application Default Credentials;
 *       if there are none either, use {@link NoCredentials} so the app still starts. Storage calls
 *       then fail at use time (surfaced as a dependency error), not at boot.</li>
 * </ul>
 * The client itself is created lazily by {@code getService()}, so no network call happens at startup.
 */
@Configuration
public class GcsClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GcsClientConfig.class);

    @Bean
    public Storage gcsStorage(GcsProperties properties) {
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (properties.projectId() != null && !properties.projectId().isBlank()) {
            builder.setProjectId(properties.projectId());
        }
        builder.setCredentials(loadCredentials(properties.credentialsFile()));
        log.info("Configuring Google Cloud Storage client (bucket '{}')", properties.bucket());
        return builder.build().getService();
    }

    private Credentials loadCredentials(String credentialsFile) {
        if (credentialsFile == null || credentialsFile.isBlank()) {
            return applicationDefaultOrNone();
        }
        Path path = Path.of(credentialsFile);
        if (!Files.exists(path)) {
            log.warn("GCS credentials file '{}' not found — falling back to environment credentials.", credentialsFile);
            return applicationDefaultOrNone();
        }
        try (InputStream in = Files.newInputStream(path)) {
            return GoogleCredentials.fromStream(in);
        } catch (IOException e) {
            // Present but invalid: fail fast rather than degrade silently.
            throw new IllegalStateException(
                    "CRED-001 Could not read GCS credentials file '" + credentialsFile + "'.", e);
        }
    }

    private Credentials applicationDefaultOrNone() {
        try {
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            log.warn("No Google Cloud credentials available — storage operations will fail until configured.");
            return NoCredentials.getInstance();
        }
    }
}
