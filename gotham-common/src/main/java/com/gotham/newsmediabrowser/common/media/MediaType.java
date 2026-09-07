package com.gotham.newsmediabrowser.common.media;

import java.util.Locale;

/**
 * The kinds of media an article can carry. Stored as the {@code multimedia.media_type} keyword and
 * used to pick the right upload size/duration limit.
 */
public enum MediaType {
    IMAGE,
    AUDIO,
    VIDEO;

    /**
     * Classifies a MIME/content type (e.g. {@code image/png}, {@code audio/mpeg}, {@code video/mp4}).
     *
     * @throws IllegalArgumentException if the content type is missing or not image/audio/video
     */
    public static MediaType fromContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("A content type is required to classify the media.");
        }
        String normalized = contentType.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("image/")) {
            return IMAGE;
        }
        if (normalized.startsWith("audio/")) {
            return AUDIO;
        }
        if (normalized.startsWith("video/")) {
            return VIDEO;
        }
        throw new IllegalArgumentException("Unsupported media content type: '" + contentType + "'.");
    }
}
