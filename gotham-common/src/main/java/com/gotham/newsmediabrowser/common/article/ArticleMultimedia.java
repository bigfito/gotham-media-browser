package com.gotham.newsmediabrowser.common.article;

import com.gotham.newsmediabrowser.common.media.MediaType;
import java.util.stream.Stream;

/**
 * A multimedia asset nested inside an article document ({@code multimedia[]}).
 *
 * <p>The {@code multimediaElementId} is <strong>app-assigned</strong> (nested documents have no
 * Elasticsearch {@code _id}); it identifies the element for later removal (P5-T03). The bytes live in
 * public GCS at {@code storageUri}. Descriptive fields feed the {@code multimedia_text} projection;
 * technical fields (dimensions, duration, codec…) are optional and enriched over time. The
 * {@code assetVector} embedding is added in P6, so it is not modeled here yet.
 */
public record ArticleMultimedia(
        String multimediaElementId,
        MediaType mediaType,
        String storageUri,
        String mimeType,
        int position,
        String caption,
        String credit,
        String title,
        String description,
        String altText,
        String originalFilename,
        Long fileSizeBytes,
        String checksum,
        Integer width,
        Integer height,
        Long durationMs,
        String codec,
        Integer bitrateKbps,
        Double frameRate,
        Integer sampleRateHz,
        Integer channels) {

    /**
     * Builds a freshly uploaded element with only the fields known at upload time; descriptive and
     * technical metadata default to empty/unknown and can be enriched later.
     */
    public static ArticleMultimedia uploaded(
            String multimediaElementId,
            MediaType mediaType,
            String storageUri,
            String mimeType,
            int position,
            String originalFilename,
            Long fileSizeBytes) {
        return new ArticleMultimedia(multimediaElementId, mediaType, storageUri, mimeType, position,
                null, null, null, null, null, originalFilename, fileSizeBytes, null,
                null, null, null, null, null, null, null, null);
    }

    /** The human text worth indexing for search (title, caption, credit, description, alt text). */
    public Stream<String> searchableText() {
        return Stream.of(title, caption, credit, description, altText)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip);
    }
}
