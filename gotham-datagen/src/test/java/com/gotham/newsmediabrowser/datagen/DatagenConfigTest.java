package com.gotham.newsmediabrowser.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Unit tests for the layered {@link DatagenConfig} resolution (defaults, CLI, validation). */
class DatagenConfigTest {

    @Test
    void defaultsMatchTheLockedPrototypeVolumes() {
        DatagenConfig config = DatagenConfig.defaultsOnly();

        assertThat(config.webBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.textModel()).isEqualTo("qwen2.5:7b-instruct");
        assertThat(config.journalists()).isEqualTo(15);
        assertThat(config.articles()).isEqualTo(25);
        assertThat(config.imagesPerArticle()).isEqualTo(5);
        assertThat(config.audioPerArticle()).isEqualTo(5);
        assertThat(config.videoPerArticle()).isEqualTo(5);
        assertThat(config.videoSeconds()).isEqualTo(5);
        assertThat(config.skipImage()).isFalse();
        // 25 articles * (5 + 5 + 5) = 375 assets.
        assertThat(config.totalMediaAssets()).isEqualTo(375);
    }

    @Test
    void cliArgumentsOverrideDefaults() {
        DatagenConfig config = DatagenConfig.load(new String[] {
                "--journalists=3", "--articles=4", "--web-base-url=http://host:9000"});

        assertThat(config.journalists()).isEqualTo(3);
        assertThat(config.articles()).isEqualTo(4);
        assertThat(config.webBaseUrl()).isEqualTo("http://host:9000");
    }

    @Test
    void bareSkipFlagsAreTrueAndReduceAssetCount() {
        DatagenConfig config = DatagenConfig.load(new String[] {"--skip-video", "--skip-audio"});

        assertThat(config.skipVideo()).isTrue();
        assertThat(config.skipAudio()).isTrue();
        assertThat(config.skipImage()).isFalse();
        // Only IMAGE remains: 25 * 5 = 125.
        assertThat(config.totalMediaAssets()).isEqualTo(125);
    }

    @Test
    void unknownOptionIsRejected() {
        assertThatThrownBy(() -> DatagenConfig.load(new String[] {"--nope=1"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--nope");
    }

    @Test
    void nonNumericVolumeIsRejected() {
        assertThatThrownBy(() -> DatagenConfig.load(new String[] {"--articles=lots"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articles");
    }

    @Test
    void positionalArgumentIsRejected() {
        assertThatThrownBy(() -> DatagenConfig.load(new String[] {"run"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("run");
    }

    @Test
    void describeListsVolumesAndSkips() {
        String described = DatagenConfig.load(new String[] {"--skip-video"}).describe();

        assertThat(described).contains("journalists");
        assertThat(described).contains("qwen2.5:7b-instruct");
        assertThat(described).contains("SKIPPED");
    }
}
