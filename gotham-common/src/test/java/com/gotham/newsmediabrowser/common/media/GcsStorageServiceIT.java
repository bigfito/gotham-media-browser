package com.gotham.newsmediabrowser.common.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.gotham.newsmediabrowser.common.config.GcsProperties;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties.Limit;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Live upload → public GET → delete round-trip against the real bucket (P5-T01 verification).
 * Skipped unless {@code GCS_BUCKET} and {@code GCS_CREDENTIALS_FILE} (an existing file) are set.
 * Runnable now with:
 *
 * <pre>mvn -pl gotham-common test -Dtest=GcsStorageServiceIT -DfailIfNoTests=false</pre>
 */
@Tag("integration")
class GcsStorageServiceIT {

    private static GcsStorageService service;

    @BeforeAll
    static void connect() throws Exception {
        String bucket = System.getenv("GCS_BUCKET");
        String project = System.getenv("GCS_PROJECT");
        String credentialsFile = System.getenv("GCS_CREDENTIALS_FILE");
        assumeTrue(bucket != null && !bucket.isBlank(), "GCS_BUCKET not set — skipping live GCS test");
        assumeTrue(credentialsFile != null && Files.exists(Path.of(credentialsFile)),
                "GCS credentials file missing — skipping live GCS test");

        GoogleCredentials credentials;
        try (InputStream in = Files.newInputStream(Path.of(credentialsFile))) {
            credentials = GoogleCredentials.fromStream(in);
        }
        Storage storage = StorageOptions.newBuilder()
                .setProjectId(project)
                .setCredentials(credentials)
                .build()
                .getService();

        GcsProperties gcs = new GcsProperties(project, bucket, credentialsFile);
        MediaLimitsProperties limits = new MediaLimitsProperties(
                new Limit(DataSize.ofMegabytes(10), null),
                new Limit(DataSize.ofMegabytes(20), Duration.ofMinutes(5)),
                new Limit(DataSize.ofMegabytes(50), Duration.ofSeconds(90)));
        service = new GcsStorageService(storage, gcs, limits);
    }

    @Test
    void uploadIsPubliclyReadableThenDeletable() throws Exception {
        byte[] payload = ("gotham-it-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
        String uri = service.upload(MediaType.IMAGE, "probe.txt", "text/plain; charset=utf-8", payload, null);
        assertThat(uri).startsWith("https://storage.googleapis.com/");

        try {
            HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(uri)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).as("public GET of uploaded object").isEqualTo(200);
            assertThat(response.body()).isEqualTo(payload);
        } finally {
            assertThat(service.delete(uri)).isTrue();
        }
    }
}
