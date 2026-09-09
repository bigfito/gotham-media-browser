package com.gotham.newsmediabrowser.common.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ContainerDurationParserTest {

    @Test
    void imagesHaveNoDuration() {
        assertThat(ContainerDurationParser.probe(MediaType.IMAGE, new byte[] {1, 2, 3})).isEmpty();
    }

    @Test
    void readsOneSecondWav() {
        byte[] wav = silentWav(8000, 8000);
        assertThat(ContainerDurationParser.probe(MediaType.AUDIO, wav))
                .contains(Duration.ofSeconds(1));
    }

    @Test
    void readsMp4MvhdDuration() {
        byte[] mp4 = minimalMp4(1000, 4500);
        assertThat(ContainerDurationParser.probe(MediaType.VIDEO, mp4))
                .contains(Duration.ofMillis(4500));
    }

    @Test
    void unknownAudioIsEmpty() {
        assertThat(ContainerDurationParser.probe(MediaType.AUDIO, new byte[] {0, 1, 2, 3})).isEmpty();
    }

    static byte[] silentWav(int sampleRate, int samples) {
        int dataSize = samples * 2;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + dataSize);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(dataSize);
        buf.put(new byte[dataSize]);
        return buf.array();
    }

    /** One {@code moov/mvhd} (version 0) so the probe can read timescale/duration. */
    static byte[] minimalMp4(int timescale, int durationUnits) {
        ByteBuffer mvhd = ByteBuffer.allocate(108).order(ByteOrder.BIG_ENDIAN);
        mvhd.putInt(108);
        mvhd.put("mvhd".getBytes(StandardCharsets.US_ASCII));
        mvhd.putInt(0);
        mvhd.putInt(0);
        mvhd.putInt(0);
        mvhd.putInt(timescale);
        mvhd.putInt(durationUnits);
        while (mvhd.hasRemaining()) {
            mvhd.put((byte) 0);
        }
        byte[] mvhdBytes = mvhd.array();
        ByteBuffer moov = ByteBuffer.allocate(8 + mvhdBytes.length).order(ByteOrder.BIG_ENDIAN);
        moov.putInt(8 + mvhdBytes.length);
        moov.put("moov".getBytes(StandardCharsets.US_ASCII));
        moov.put(mvhdBytes);
        return moov.array();
    }
}
