package com.gotham.newsmediabrowser.common.media;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure-Java container inspection: reads a playback duration out of the bytes we already hold, with no
 * subprocess and no native dependency. Audio prefers the Java Sound SPI (WAV/AIFF/AU) and also parses
 * WAV directly; video reads an MP4/MOV {@code mvhd} box.
 *
 * <p>This is the <strong>fallback</strong> tier of {@link MediaDurationProbe}, not the primary one —
 * it understands only those containers, so MP3, OGG, FLAC and WebM come back empty here. Real
 * measurement across formats is {@code ffprobe}; see {@link MediaDurationProbe}.
 */
public final class ContainerDurationParser {

    private static final Logger log = LoggerFactory.getLogger(ContainerDurationParser.class);

    private ContainerDurationParser() {
    }

    /**
     * @return empty when the type has no duration (IMAGE) or the container cannot be parsed
     */
    public static Optional<Duration> probe(MediaType type, byte[] data) {
        if (type == null || type == MediaType.IMAGE || data == null || data.length == 0) {
            return Optional.empty();
        }
        if (type == MediaType.AUDIO) {
            Optional<Duration> fromSpi = fromAudioSystem(data);
            if (fromSpi.isPresent()) {
                return fromSpi;
            }
            return wavDuration(data);
        }
        return mp4Duration(data);
    }

    private static Optional<Duration> fromAudioSystem(byte[] data) {
        try {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(data));
            long frames = format.getFrameLength();
            float frameRate = format.getFormat().getFrameRate();
            if (frames > 0 && frameRate > 0) {
                return Optional.of(Duration.ofMillis(Math.round(frames / frameRate * 1000.0)));
            }
        } catch (UnsupportedAudioFileException | java.io.IOException e) {
            log.debug("Java Sound could not read audio duration: {}", e.toString());
        }
        return Optional.empty();
    }

    static Optional<Duration> wavDuration(byte[] data) {
        if (data.length < 44 || !ascii(data, 0, 4).equals("RIFF") || !ascii(data, 8, 4).equals("WAVE")) {
            return Optional.empty();
        }
        int offset = 12;
        Integer byteRate = null;
        Integer dataBytes = null;
        while (offset + 8 <= data.length) {
            String chunk = ascii(data, offset, 4);
            int size = leU32(data, offset + 4);
            int body = offset + 8;
            if ("fmt ".equals(chunk) && body + 16 <= data.length) {
                byteRate = leU32(data, body + 8);
            } else if ("data".equals(chunk)) {
                dataBytes = size;
            }
            long next = (long) body + Integer.toUnsignedLong(size);
            if ((size & 1) == 1) {
                next++;
            }
            if (next <= offset || next > data.length) {
                break;
            }
            offset = (int) next;
        }
        if (byteRate != null && byteRate > 0 && dataBytes != null && dataBytes >= 0) {
            long millis = Math.round(dataBytes * 1000.0 / byteRate);
            return Optional.of(Duration.ofMillis(millis));
        }
        return Optional.empty();
    }

    static Optional<Duration> mp4Duration(byte[] data) {
        try {
            return findMvhd(data, 0, data.length);
        } catch (RuntimeException e) {
            log.debug("MP4 duration parse failed: {}", e.toString());
            return Optional.empty();
        }
    }

    private static Optional<Duration> findMvhd(byte[] data, int start, int end) {
        int offset = start;
        while (offset + 8 <= end) {
            long size = u32(data, offset);
            String type = ascii(data, offset + 4, 4);
            int header = 8;
            if (size == 1) {
                if (offset + 16 > end) {
                    return Optional.empty();
                }
                size = u64(data, offset + 8);
                header = 16;
            } else if (size == 0) {
                size = end - offset;
            }
            if (size < header) {
                return Optional.empty();
            }
            int boxEnd = (int) Math.min(end, offset + size);
            int payload = offset + header;
            if ("moov".equals(type) || "trak".equals(type) || "mdia".equals(type)) {
                Optional<Duration> nested = findMvhd(data, payload, boxEnd);
                if (nested.isPresent()) {
                    return nested;
                }
            } else if ("mvhd".equals(type)) {
                return parseMvhd(data, payload, boxEnd);
            }
            if (size > Integer.MAX_VALUE - offset) {
                break;
            }
            offset += (int) size;
            if (offset <= start) {
                break;
            }
        }
        return Optional.empty();
    }

    private static Optional<Duration> parseMvhd(byte[] data, int payload, int boxEnd) {
        if (payload >= boxEnd) {
            return Optional.empty();
        }
        int version = data[payload] & 0xff;
        int timescale;
        long durationUnits;
        if (version == 1) {
            if (payload + 3 + 8 + 8 + 4 + 8 > boxEnd) {
                return Optional.empty();
            }
            int p = payload + 4;
            p += 16;
            timescale = (int) u32(data, p);
            durationUnits = u64(data, p + 4);
        } else {
            if (payload + 4 + 4 + 4 + 4 + 4 > boxEnd) {
                return Optional.empty();
            }
            int p = payload + 4;
            p += 8;
            timescale = (int) u32(data, p);
            durationUnits = u32(data, p + 4);
        }
        if (timescale <= 0 || durationUnits <= 0) {
            return Optional.empty();
        }
        long millis = Math.round(durationUnits * 1000.0 / timescale);
        return Optional.of(Duration.ofMillis(millis));
    }

    private static String ascii(byte[] data, int offset, int len) {
        if (offset < 0 || offset + len > data.length) {
            return "";
        }
        return new String(data, offset, len, StandardCharsets.US_ASCII);
    }

    private static int leU32(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static long u32(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt() & 0xffff_ffffL;
    }

    private static long u64(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.BIG_ENDIAN).getLong();
    }
}
