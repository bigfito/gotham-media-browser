package com.gotham.newsmediabrowser.common.imagebind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gotham.newsmediabrowser.common.media.MediaType;
import org.junit.jupiter.api.Test;

/** Unit tests for the deterministic offline embedder (P6-T02 stub mode). */
class StubImageBindClientTest {

    private final StubImageBindClient client = new StubImageBindClient();

    @Test
    void embedTextReturnsUnit1024Vector() {
        float[] vector = client.embedText("gotham transit vote");

        assertThat(vector).hasSize(ImageBindClient.EMBEDDING_DIM);
        assertThat(l2Norm(vector)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    void sameTextYieldsIdenticalVector() {
        assertThat(client.embedText("same input")).containsExactly(client.embedText("same input"));
    }

    @Test
    void differentTextYieldsDifferentVector() {
        assertThat(client.embedText("alpha")).isNotEqualTo(client.embedText("beta"));
    }

    @Test
    void embedMediaReturnsDeterministic1024Vector() {
        byte[] data = {1, 2, 3, 4, 5};

        float[] first = client.embedMedia(MediaType.IMAGE, data, "x.png", "image/png");
        float[] second = client.embedMedia(MediaType.IMAGE, data, "x.png", "image/png");

        assertThat(first).hasSize(ImageBindClient.EMBEDDING_DIM).containsExactly(second);
    }

    @Test
    void sameBytesUnderDifferentModalitiesDiffer() {
        byte[] data = {9, 9, 9};

        assertThat(client.embedMedia(MediaType.IMAGE, data, "a", "image/png"))
                .isNotEqualTo(client.embedMedia(MediaType.AUDIO, data, "a", "audio/wav"));
    }

    @Test
    void blankTextIsRejected() {
        assertThatThrownBy(() -> client.embedText("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyMediaIsRejected() {
        assertThatThrownBy(() -> client.embedMedia(MediaType.IMAGE, new byte[0], "x", "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private double l2Norm(float[] vector) {
        double sum = 0.0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        return Math.sqrt(sum);
    }
}
