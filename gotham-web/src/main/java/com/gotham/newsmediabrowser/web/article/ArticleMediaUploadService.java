package com.gotham.newsmediabrowser.web.article;

import com.gotham.newsmediabrowser.common.article.ArticleMultimedia;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.media.GcsStorageService;
import com.gotham.newsmediabrowser.common.media.MediaDurationProbe;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
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
 * GCS and given an app-assigned id and a position. Image dimensions and audio/video duration are
 * filled in best-effort so GCS duration limits can be enforced. ImageBind embeddings are attached
 * when the embedder is up.
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

    /** Uploads files with no descriptive metadata. */
    public List<ArticleMultimedia> upload(MultipartFile[] files, int startPosition) {
        return upload(files, startPosition, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Classifies every non-empty file first (so a bad type never touches GCS), then uploads. If a
     * later file fails, objects already stored in this batch are deleted.
     *
     * @throws IllegalArgumentException if a file has no usable image/audio/video content type
     */
    public List<ArticleMultimedia> upload(
            MultipartFile[] files,
            int startPosition,
            List<String> titles,
            List<String> captions,
            List<String> descriptions,
            List<String> altTexts,
            List<String> credits) {

        List<PreparedUpload> prepared = prepare(files, startPosition, titles, captions, descriptions, altTexts,
                credits);
        List<ArticleMultimedia> stored = new ArrayList<>();
        try {
            for (PreparedUpload item : prepared) {
                stored.add(store(item));
            }
            return stored;
        } catch (RuntimeException e) {
            remove(stored);
            throw e;
        }
    }

    /**
     * Deletes the GCS objects backing the given media elements.
     *
     * @param media the elements whose stored objects should be removed (a {@code null} list is a no-op)
     */
    public void remove(List<ArticleMultimedia> media) {
        if (media == null) {
            return;
        }
        for (ArticleMultimedia element : media) {
            storageService.delete(element.storageUri());
        }
    }

    private List<PreparedUpload> prepare(
            MultipartFile[] files,
            int startPosition,
            List<String> titles,
            List<String> captions,
            List<String> descriptions,
            List<String> altTexts,
            List<String> credits) {

        List<PreparedUpload> prepared = new ArrayList<>();
        if (files == null) {
            return prepared;
        }
        int metaIndex = 0;
        int position = startPosition;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            byte[] data = readBytes(file);
            String contentType = file.getContentType();
            MediaType type = MediaType.fromContentType(contentType);
            prepared.add(new PreparedUpload(
                    data,
                    type,
                    contentType,
                    file.getOriginalFilename(),
                    position++,
                    at(titles, metaIndex),
                    at(captions, metaIndex),
                    at(descriptions, metaIndex),
                    at(altTexts, metaIndex),
                    at(credits, metaIndex)));
            metaIndex++;
        }
        return prepared;
    }

    private ArticleMultimedia store(PreparedUpload item) {
        Duration duration = MediaDurationProbe.probe(item.type(), item.data()).orElse(null);
        String storageUri = storageService.upload(
                item.type(), item.originalFilename(), item.contentType(), item.data(), duration);

        Integer width = null;
        Integer height = null;
        if (item.type() == MediaType.IMAGE) {
            int[] dimensions = imageDimensions(item.data());
            if (dimensions != null) {
                width = dimensions[0];
                height = dimensions[1];
            }
        }
        Long durationMs = duration != null ? duration.toMillis() : null;
        List<Float> assetVector = embedAsset(item.type(), item.data(), item.originalFilename(), item.contentType());

        return new ArticleMultimedia(
                UUID.randomUUID().toString(), item.type(), storageUri, item.contentType(), item.position(),
                item.caption(), item.credit(), item.title(), item.description(), item.altText(),
                item.originalFilename(), (long) item.data().length, null,
                width, height, durationMs, null, null, null, null, null, assetVector);
    }

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

    private static String at(List<String> values, int index) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String raw;
        if (index < values.size()) {
            raw = values.get(index);
        } else if (values.size() == 1) {
            raw = values.get(0);
        } else {
            return null;
        }
        return raw != null && !raw.isBlank() ? raw.strip() : null;
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

    private record PreparedUpload(
            byte[] data,
            MediaType type,
            String contentType,
            String originalFilename,
            int position,
            String title,
            String caption,
            String description,
            String altText,
            String credit) {}
}
