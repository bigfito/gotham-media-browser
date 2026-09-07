package com.gotham.newsmediabrowser.common.article;

import com.gotham.newsmediabrowser.common.media.MediaType;
import java.util.List;
import java.util.stream.Stream;

/**
 * A multimedia asset nested inside an article document ({@code multimedia[]}).
 *
 * <p>The {@code multimediaElementId} is <strong>app-assigned</strong> (nested documents have no
 * Elasticsearch {@code _id}); it identifies the element for removal (P5-T03). The bytes live in
 * public GCS at {@code storageUri}. Descriptive fields feed the {@code multimedia_text} projection;
 * technical fields (dimensions, duration, codec…) are optional and enriched over time. The
 * {@code assetVector} is the 1024-d ImageBind embedding of the asset (P6-T03), computed at upload and
 * round-tripped so it survives edits; {@code null} until embedded.
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
        Integer channels,
        List<Float> assetVector) {

    /**
     * Builds a freshly uploaded element with only the fields known at upload time; descriptive and
     * technical metadata default to empty/unknown and can be enriched later. The embedding is
     * attached separately via {@link #withAssetVector(List)}.
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
                null, null, null, null, null, null, null, null, null);
    }

    /** Returns a copy with the given ImageBind embedding attached. */
    public ArticleMultimedia withAssetVector(List<Float> assetVector) {
        return new ArticleMultimedia(multimediaElementId, mediaType, storageUri, mimeType, position,
                caption, credit, title, description, altText, originalFilename, fileSizeBytes, checksum,
                width, height, durationMs, codec, bitrateKbps, frameRate, sampleRateHz, channels, assetVector);
    }

    /** The human text worth indexing for search (title, caption, credit, description, alt text). */
    public Stream<String> searchableText() {
        return Stream.of(title, caption, credit, description, altText)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip);
    }
}
