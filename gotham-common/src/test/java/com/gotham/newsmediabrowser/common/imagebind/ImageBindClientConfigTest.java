package com.gotham.newsmediabrowser.common.imagebind;

import static org.assertj.core.api.Assertions.assertThat;

import com.gotham.newsmediabrowser.common.config.ImageBindProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Verifies the bean factory selects the backend from {@code gotham.imagebind.stub}. */
class ImageBindClientConfigTest {

    private final ImageBindClientConfig config = new ImageBindClientConfig();

    @Test
    void stubTrueYieldsStubClient() {
        ImageBindClient client = config.imageBindClient(
                new ImageBindProperties("http://imagebind-service:8081", true, Duration.ofSeconds(60)));

        assertThat(client).isInstanceOf(StubImageBindClient.class);
    }

    @Test
    void stubFalseYieldsHttpClient() {
        ImageBindClient client = config.imageBindClient(
                new ImageBindProperties("http://imagebind-service:8081", false, Duration.ofSeconds(60)));

        assertThat(client).isInstanceOf(HttpImageBindClient.class);
    }
}
