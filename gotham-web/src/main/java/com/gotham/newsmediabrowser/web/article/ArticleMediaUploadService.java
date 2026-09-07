package com.gotham.newsmediabrowser.web.article;

import com.gotham.newsmediabrowser.common.article.ArticleMultimedia;
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

    public ArticleMediaUploadService(GcsStorageService storageService) {
        this.storageService = storageService;
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

        return new ArticleMultimedia(
                UUID.randomUUID().toString(), type, storageUri, contentType, position,
                null, null, null, null, null, originalFilename, (long) data.length, null,
                width, height, null, null, null, null, null, null);
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
