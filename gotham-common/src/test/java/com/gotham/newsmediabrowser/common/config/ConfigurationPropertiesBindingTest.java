package com.gotham.newsmediabrowser.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Verifies the {@code gotham.*} configuration records bind from properties, that placeholder
 * detection works, and that the Elasticsearch API key never appears in {@code toString()}.
 */
class ConfigurationPropertiesBindingTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropsConfig.class);

    @Test
    void bindsElasticsearchAndMasksApiKey() {
        runner.withPropertyValues(
                        "gotham.elasticsearch.endpoint=https://es.example:443",
                        "gotham.elasticsearch.api-key=SUPER_SECRET_KEY")
                .run(context -> {
                    ElasticsearchProperties props = context.getBean(ElasticsearchProperties.class);
                    assertThat(props.endpoint()).isEqualTo("https://es.example:443");
                    assertThat(props.apiKey()).isEqualTo("SUPER_SECRET_KEY");
                    assertThat(props.isConfigured()).isTrue();
                    // The secret must never leak through toString().
                    assertThat(props.toString()).doesNotContain("SUPER_SECRET_KEY").contains("****");
                });
    }

    @Test
    void placeholdersAreReportedAsNotConfigured() {
        runner.withPropertyValues(
                        "gotham.elasticsearch.endpoint=https://YOUR-ES-ENDPOINT",
                        "gotham.elasticsearch.api-key=YOUR_API_KEY",
                        "gotham.gcs.project-id=YOUR_GCP_PROJECT",
                        "gotham.gcs.bucket=YOUR_PUBLIC_BUCKET")
                .run(context -> {
                    assertThat(context.getBean(ElasticsearchProperties.class).isConfigured()).isFalse();
                    assertThat(context.getBean(GcsProperties.class).isConfigured()).isFalse();
                });
    }

    @Test
    void bindsGcsAndImageBind() {
        runner.withPropertyValues(
                        "gotham.gcs.project-id=elastic-sa",
                        "gotham.gcs.bucket=my-bucket",
                        "gotham.gcs.credentials-file=secrets/gcp-sa.json",
                        "gotham.imagebind.base-url=http://imagebind-service:8081")
                .run(context -> {
                    GcsProperties gcs = context.getBean(GcsProperties.class);
                    assertThat(gcs.projectId()).isEqualTo("elastic-sa");
                    assertThat(gcs.bucket()).isEqualTo("my-bucket");
                    assertThat(gcs.credentialsFile()).isEqualTo("secrets/gcp-sa.json");
                    assertThat(gcs.isConfigured()).isTrue();
                    assertThat(context.getBean(ImageBindProperties.class).baseUrl())
                            .isEqualTo("http://imagebind-service:8081");
                });
    }

    @Test
    void bindsMediaLimits() {
        runner.withPropertyValues(
                        "gotham.media.image.max-size=10MB",
                        "gotham.media.audio.max-size=20MB",
                        "gotham.media.audio.max-duration=5m",
                        "gotham.media.video.max-size=50MB",
                        "gotham.media.video.max-duration=90s")
                .run(context -> {
                    MediaLimitsProperties media = context.getBean(MediaLimitsProperties.class);
                    assertThat(media.image().maxSize()).isEqualTo(DataSize.ofMegabytes(10));
                    assertThat(media.image().maxDuration()).isNull();
                    assertThat(media.audio().maxSize()).isEqualTo(DataSize.ofMegabytes(20));
                    assertThat(media.audio().maxDuration()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(media.video().maxSize()).isEqualTo(DataSize.ofMegabytes(50));
                    assertThat(media.video().maxDuration()).isEqualTo(Duration.ofSeconds(90));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            ElasticsearchProperties.class,
            GcsProperties.class,
            ImageBindProperties.class,
            MediaLimitsProperties.class
    })
    static class PropsConfig {
    }
}
