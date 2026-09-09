package com.gotham.newsmediabrowser.web.search;

import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.article.MultimediaSearchPage;
import com.gotham.newsmediabrowser.common.article.MultimediaSemanticSearchService;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties.Limit;
import com.gotham.newsmediabrowser.common.error.BadRequestException;
import com.gotham.newsmediabrowser.common.error.MediaLimitException;
import com.gotham.newsmediabrowser.common.imagebind.ImageBindClient;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multimedia vector search from an uploaded file ({@code docs/elasticsearch-search-methods.md} §10):
 * the file is classified and size-checked, embedded with ImageBind, then matched against the nested
 * {@code multimedia.asset_vector} using the same nested kNN as semantic search (P8-T01). There is no
 * BM25 leg — the query is a media file, not text.
 *
 * <p>Because ImageBind maps every modality into one space, an uploaded image can match audio or
 * video assets, so results are not restricted to the uploaded file's own media type; the caller's
 * {@code mediaTypes} filter (if any) still applies.
 */
@Service
public class MultimediaVectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(MultimediaVectorSearchService.class);

    private final ImageBindClient imageBindClient;
    private final MediaLimitsProperties mediaLimits;
    private final MultimediaSemanticSearchService multimediaSemanticSearchService;

    public MultimediaVectorSearchService(ImageBindClient imageBindClient,
                                         MediaLimitsProperties mediaLimits,
                                         MultimediaSemanticSearchService multimediaSemanticSearchService) {
        this.imageBindClient = imageBindClient;
        this.mediaLimits = mediaLimits;
        this.multimediaSemanticSearchService = multimediaSemanticSearchService;
    }

    /**
     * Embeds the uploaded file and runs nested vector search.
     *
     * @throws BadRequestException if the file is empty or not an image/audio/video type (HTTP 400)
     * @throws MediaLimitException if the file exceeds its media type's size limit (HTTP 413)
     */
    public MultimediaSearchPage search(MultipartFile file,
                                       List<ArticleStatus> statuses,
                                       String section,
                                       String language,
                                       Instant publishedFrom,
                                       Instant publishedTo,
                                       List<MediaType> mediaTypes,
                                       int page,
                                       int size) {
        return search(embed(file), statuses, section, language, publishedFrom, publishedTo, mediaTypes, page, size);
    }

    /**
     * Classifies, size-checks, and embeds an uploaded file. The vector can be stored in the HTTP
     * session so pagination/filter GETs do not require re-uploading the file.
     */
    public float[] embed(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose an image, audio, or video file for vector search.");
        }
        MediaType type = classify(file.getContentType());
        byte[] data = readBytes(file);
        enforceSize(type, data);
        float[] vector = imageBindClient.embedMedia(type, data, file.getOriginalFilename(), file.getContentType());
        log.debug("Vector search: embedded {} upload ({} bytes)", type, data.length);
        return vector;
    }

    public MultimediaSearchPage search(float[] vector,
                                       List<ArticleStatus> statuses,
                                       String section,
                                       String language,
                                       Instant publishedFrom,
                                       Instant publishedTo,
                                       List<MediaType> mediaTypes,
                                       int page,
                                       int size) {
        return multimediaSemanticSearchService.searchByVector(
                vector, statuses, section, language, publishedFrom, publishedTo, mediaTypes, page, size);
    }

    private static MediaType classify(String contentType) {
        try {
            return MediaType.fromContentType(contentType);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Upload an image, audio, or video file for vector search.", e);
        }
    }

    private void enforceSize(MediaType type, byte[] data) {
        Limit limit = limitFor(type);
        long maxBytes = limit.maxSize().toBytes();
        if (data.length > maxBytes) {
            throw new MediaLimitException(type + " file is " + data.length + " bytes, over the "
                    + limit.maxSize().toMegabytes() + " MB limit.");
        }
    }

    private Limit limitFor(MediaType type) {
        return switch (type) {
            case IMAGE -> mediaLimits.image();
            case AUDIO -> mediaLimits.audio();
            case VIDEO -> mediaLimits.video();
        };
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file. Please try again.", e);
        }
    }
}
