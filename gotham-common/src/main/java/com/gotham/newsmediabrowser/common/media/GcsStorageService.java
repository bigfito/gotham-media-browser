package com.gotham.newsmediabrowser.common.media;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.gotham.newsmediabrowser.common.config.GcsProperties;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties;
import com.gotham.newsmediabrowser.common.config.MediaLimitsProperties.Limit;
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.error.MediaLimitException;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Uploads and deletes public media objects in Google Cloud Storage.
 *
 * <p>Enforces the per-type size and duration limits before any upload, so an over-limit file is
 * rejected ({@link MediaLimitException}, HTTP 413) without touching the bucket. Uploaded objects are
 * public-read (the bucket is configured for public access), so their {@code storage_uri} is a plain
 * {@code https://storage.googleapis.com/<bucket>/<object>} URL that plays directly in the browser.
 * Storage failures surface as a {@link DependencyException} (HTTP 503) naming the service.
 */
@Service
public class GcsStorageService {

    private static final Logger log = LoggerFactory.getLogger(GcsStorageService.class);
    private static final String SERVICE = "Google Cloud Storage";
    private static final String PUBLIC_BASE = "https://storage.googleapis.com/";

    private final Storage storage;
    private final GcsProperties gcsProperties;
    private final MediaLimitsProperties mediaLimits;

    public GcsStorageService(Storage storage, GcsProperties gcsProperties, MediaLimitsProperties mediaLimits) {
        this.storage = storage;
        this.gcsProperties = gcsProperties;
        this.mediaLimits = mediaLimits;
    }

    /**
     * Uploads a media file after checking it against its type's limits.
     *
     * @param type             the media kind (selects the applicable limit)
     * @param originalFilename the client's filename (used only to build a readable object name)
     * @param contentType      MIME type stored on the object
     * @param data             the file bytes
     * @param duration         playback duration for audio/video; {@code null} for images or when unknown
     * @return the public HTTPS {@code storage_uri} of the stored object
     * @throws MediaLimitException if the file exceeds its size or duration limit
     * @throws DependencyException if the upload to GCS fails
     */
    public String upload(MediaType type, String originalFilename, String contentType, byte[] data, Duration duration) {
        enforceLimits(type, data, duration);

        String objectName = objectName(type, originalFilename);
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(gcsProperties.bucket(), objectName))
                .setContentType(contentType)
                .build();
        try {
            // The bucket uses uniform bucket-level access, so per-object ACLs are not set here.
            // Public readability comes from the bucket's IAM policy (allUsers:objectViewer); see
            // secrets/README or the runbook for that one-time bucket configuration.
            storage.create(blobInfo, data);
        } catch (StorageException e) {
            throw new DependencyException(SERVICE, e);
        }
        String uri = PUBLIC_BASE + gcsProperties.bucket() + "/" + objectName;
        log.info("Uploaded {} object ({} bytes) to {}", type, data.length, objectName);
        return uri;
    }

    /**
     * Deletes the object identified by a {@code storage_uri} previously returned from {@link #upload}.
     *
     * @return {@code true} if an object was deleted, {@code false} if it did not exist or the URI is
     *     not one of ours
     * @throws DependencyException if the delete call to GCS fails
     */
    public boolean delete(String storageUri) {
        String objectName = objectNameFromUri(storageUri);
        if (objectName == null) {
            log.warn("Ignoring delete for URI not owned by this bucket: {}", storageUri);
            return false;
        }
        try {
            boolean deleted = storage.delete(BlobId.of(gcsProperties.bucket(), objectName));
            log.info("Delete GCS object {} -> {}", objectName, deleted);
            return deleted;
        } catch (StorageException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    private void enforceLimits(MediaType type, byte[] data, Duration duration) {
        Limit limit = limitFor(type);
        long maxBytes = limit.maxSize().toBytes();
        if (data.length > maxBytes) {
            throw new MediaLimitException(type + " file is " + data.length + " bytes, over the "
                    + limit.maxSize().toMegabytes() + " MB limit.");
        }
        if (limit.maxDuration() != null && duration != null && duration.compareTo(limit.maxDuration()) > 0) {
            throw new MediaLimitException(type + " runs " + duration.toSeconds() + "s, over the "
                    + limit.maxDuration().toSeconds() + "s limit.");
        }
    }

    private Limit limitFor(MediaType type) {
        return switch (type) {
            case IMAGE -> mediaLimits.image();
            case AUDIO -> mediaLimits.audio();
            case VIDEO -> mediaLimits.video();
        };
    }

    private String objectName(MediaType type, String originalFilename) {
        return "media/" + type.name().toLowerCase(Locale.ROOT) + "/"
                + UUID.randomUUID() + "-" + sanitize(originalFilename);
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        // Keep a readable but safe object suffix: strip paths, allow word chars, dot and dash.
        String base = filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String objectNameFromUri(String storageUri) {
        String prefix = PUBLIC_BASE + gcsProperties.bucket() + "/";
        return storageUri != null && storageUri.startsWith(prefix) ? storageUri.substring(prefix.length()) : null;
    }
}
