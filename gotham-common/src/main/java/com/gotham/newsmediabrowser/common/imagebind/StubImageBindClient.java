package com.gotham.newsmediabrowser.common.imagebind;

import com.gotham.newsmediabrowser.common.media.MediaType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic, offline stand-in for {@link ImageBindClient}. Selected by
 * {@code gotham.imagebind.stub=true} so CI and local runs work without the ImageBind model or
 * network.
 *
 * <p>The same input always yields the same L2-normalized {@value ImageBindClient#EMBEDDING_DIM}-d
 * vector (seeded from a SHA-256 hash of the content), mirroring the service's own stub backend. The
 * vectors are NOT semantically meaningful — use the HTTP client for real search quality.
 */
public class StubImageBindClient implements ImageBindClient {

    @Override
    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return vector(("text:" + text).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public float[] embedMedia(MediaType type, byte[] data, String filename, String contentType) {
        if (type == null || data == null || data.length == 0) {
            throw new IllegalArgumentException("media type and non-empty data are required");
        }
        byte[] prefix = (type.name().toLowerCase() + ":").getBytes(StandardCharsets.UTF_8);
        byte[] seed = new byte[prefix.length + data.length];
        System.arraycopy(prefix, 0, seed, 0, prefix.length);
        System.arraycopy(data, 0, seed, prefix.length, data.length);
        return vector(seed);
    }

    /**
     * Builds a stable, L2-normalized {@value ImageBindClient#EMBEDDING_DIM}-d vector from a content
     * hash: the SHA-256 digest seeds a repeatable byte stream mapped into [-1, 1], then normalized.
     */
    private float[] vector(byte[] seed) {
        byte[] stream = new byte[EMBEDDING_DIM];
        byte[] digest = sha256(seed);
        int filled = 0;
        int counter = 0;
        while (filled < EMBEDDING_DIM) {
            byte[] block = sha256(concat(digest, intBytes(counter)));
            int take = Math.min(block.length, EMBEDDING_DIM - filled);
            System.arraycopy(block, 0, stream, filled, take);
            filled += take;
            counter++;
        }

        float[] values = new float[EMBEDDING_DIM];
        double sumSquares = 0.0;
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            float value = (float) (((stream[i] & 0xFF) / 127.5) - 1.0);
            values[i] = value;
            sumSquares += (double) value * value;
        }
        double norm = Math.sqrt(sumSquares);
        if (norm > 0.0) {
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                values[i] = (float) (values[i] / norm);
            }
        }
        return values;
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private byte[] intBytes(int value) {
        return new byte[] {
            (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
        };
    }
}
