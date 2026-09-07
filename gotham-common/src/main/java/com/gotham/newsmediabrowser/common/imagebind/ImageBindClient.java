package com.gotham.newsmediabrowser.common.imagebind;

import com.gotham.newsmediabrowser.common.media.MediaType;

/**
 * Produces ImageBind embeddings for the Gotham prototype: text and media (image / audio / video)
 * are mapped into the same {@value #EMBEDDING_DIM}-dimensional vector space.
 *
 * <p>Two implementations back this contract (Strategy):
 * <ul>
 *   <li>{@link HttpImageBindClient} — calls the real {@code imagebind-service} over HTTP.</li>
 *   <li>{@link StubImageBindClient} — deterministic offline vectors for CI / weight-less runs,
 *       selected by {@code gotham.imagebind.stub=true}.</li>
 * </ul>
 * Callers depend only on this interface, never on which backend is wired.
 */
public interface ImageBindClient {

    /** Shared embedding dimensionality across every modality. */
    int EMBEDDING_DIM = 1024;

    /**
     * Embeds a piece of text.
     *
     * @param text the text to embed (must not be blank)
     * @return a {@value #EMBEDDING_DIM}-length vector
     */
    float[] embedText(String text);

    /**
     * Embeds a media asset by sending its bytes to the matching modality endpoint.
     *
     * @param type        the media kind (selects image/audio/video)
     * @param data        the raw file bytes
     * @param filename    original filename (used for the multipart part; may be {@code null})
     * @param contentType MIME type of the asset (may be {@code null})
     * @return a {@value #EMBEDDING_DIM}-length vector
     */
    float[] embedMedia(MediaType type, byte[] data, String filename, String contentType);
}
