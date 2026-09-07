package com.gotham.newsmediabrowser.web.article;

import com.gotham.newsmediabrowser.common.article.ArticleMultimedia;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.media.GcsStorageService;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Turns uploaded article files into nested {@link ArticleMultimedia} elements: each file is stored in
 * GCS and given an app-assigned id and a position. Image dimensions are filled in best-effort;
 * duration/codec metadata is left for later enrichment (P6 / datagen).
 *
 * <p>Size and duration limits are enforced by {@link GcsStorageService} before the object is stored.
 */
@Service
public class ArticleMediaUploadService {

    private static final Logger log = LoggerFactory.getLogger(ArticleMediaUploadService.class);

    private final GcsStorageService storageService;
    private final ImageBindClient imageBindClient;

    public ArticleMediaUploadService(GcsStorageService storageService, ImageBindClient imageBindClient) {
        this.storageService = storageService;
        this.imageBindClient = imageBindClient;
    }

    /**
     * Uploads the given files (skipping empty slots) and returns the nested elements, numbered from
     * {@code startPosition}.
     *
     * @throws IllegalArgumentException if a file has no usable image/audio/video content type
     */
    public List<ArticleMultimedia> upload(MultipartFile[] files, int startPosition) {
        List<ArticleMultimedia> elements = new ArrayList<>();
        if (files == null) {
            return elements;
        }
        int position = startPosition;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            elements.add(toElement(file, position++));
        }
        return elements;
    }

    /**
     * Deletes the GCS objects backing the given media elements. Called before the article document is
     * updated or removed, so a retry after a storage failure re-attempts the same (idempotent) deletes
     * and no orphaned objects are left behind.
     *
     * @param media the elements whose stored objects should be removed (a {@code null} list is a no-op)
     * @throws com.gotham.newsmediabrowser.common.error.DependencyException if a delete call to GCS fails
     */
    public void remove(List<ArticleMultimedia> media) {
        if (media == null) {
            return;
        }
        for (ArticleMultimedia element : media) {
            storageService.delete(element.storageUri());
        }
    }

    private ArticleMultimedia toElement(MultipartFile file, int position) {
        byte[] data = readBytes(file);
        String contentType = file.getContentType();
        MediaType type = MediaType.fromContentType(contentType);
        String originalFilename = file.getOriginalFilename();

        String storageUri = storageService.upload(type, originalFilename, contentType, data, null);

        Integer width = null;
        Integer height = null;
        if (type == MediaType.IMAGE) {
            int[] dimensions = imageDimensions(data);
            if (dimensions != null) {
                width = dimensions[0];
                height = dimensions[1];
            }
        }

        List<Float> assetVector = embedAsset(type, data, originalFilename, contentType);

        return new ArticleMultimedia(
                UUID.randomUUID().toString(), type, storageUri, contentType, position,
                null, null, null, null, null, originalFilename, (long) data.length, null,
                width, height, null, null, null, null, null, null, assetVector);
    }

    /**
     * Embeds the asset with ImageBind, or returns {@code null} (logged) if the embedder is
     * unavailable — the media is still stored, just without its {@code asset_vector}.
     */
    private List<Float> embedAsset(MediaType type, byte[] data, String filename, String contentType) {
        try {
            float[] vector = imageBindClient.embedMedia(type, data, filename, contentType);
            List<Float> list = new ArrayList<>(vector.length);
            for (float value : vector) {
                list.add(value);
            }
            return list;
        } catch (RuntimeException e) {
            log.warn("Skipping asset_vector for {} — ImageBind unavailable: {}", filename, e.toString());
            return null;
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read uploaded file: " + file.getOriginalFilename(), e);
        }
    }

    private int[] imageDimensions(byte[] data) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image != null) {
                return new int[] {image.getWidth(), image.getHeight()};
            }
        } catch (IOException e) {
            log.debug("Could not read image dimensions: {}", e.getMessage());
        }
        return null;
    }
}
