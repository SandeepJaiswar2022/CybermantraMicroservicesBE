package com.lms.content.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;

/**
 * Storage provider abstraction.
 *
 * <p>This interface is the key extension point for the storage layer.
 * Implementing classes: {@link CloudinaryStorageService}, {@link MinioStorageService}.
 *
 * <p>Future extension: {@code CdnStorageService} (upload to origin, serve via CDN edge).
 *
 * <p>Design rationale: by coding to this interface, the entire service is storage-agnostic.
 * Switching from Cloudinary to MinIO (or adding S3 later) requires zero changes to
 * business logic — only the implementation bean changes.
 */
public interface StorageService {

    /**
     * Upload a file to the storage backend.
     *
     * @param file         the multipart file from the HTTP request
     * @param folder       logical folder/prefix (e.g., "videos", "thumbnails")
     * @param resourceType storage resource type hint (e.g., "video", "image", "raw")
     * @return {@link UploadResult} containing the storage key and any provider metadata
     */
    UploadResult upload(MultipartFile file, String folder, String resourceType);

    /**
     * Generate a time-limited, signed URL for secure content access.
     *
     * <p>For Cloudinary: signs the URL with the API secret.
     * For MinIO: generates a presigned GET URL via AWS Signature V4.
     *
     * @param storageKey    the key returned by {@link #upload}
     * @param resourceType  "video", "image", or "raw"
     * @param expirySeconds TTL in seconds
     * @return signed URL string
     */
    String generateSignedUrl(String storageKey, String resourceType, long expirySeconds);

    /**
     * Delete a file from storage.
     *
     * @param storageKey   the key returned by {@link #upload}
     * @param resourceType "video", "image", or "raw"
     */
    void delete(String storageKey, String resourceType);

    /**
     * Stream file bytes — used for proxy streaming when direct signed URLs
     * cannot be generated (e.g., access-controlled MinIO buckets).
     *
     * @param storageKey the key returned by {@link #upload}
     * @return raw InputStream; caller is responsible for closing
     */
    InputStream streamContent(String storageKey);

    // ─── Result record ────────────────────────────────────────────────────

    /**
     * Immutable result from a successful upload operation.
     *
     * @param storageKey      provider-specific identifier; stored in DB, never in API response
     * @param publicUrl       non-signed URL (use for thumbnails only; videos use signed URLs)
     * @param fileSizeBytes   actual bytes stored
     * @param durationSeconds video duration (null for non-video)
     * @param widthPixels     video/image width (null for non-visual)
     * @param heightPixels    video/image height (null for non-visual)
     * @param metadata        any extra provider metadata (format, etag, etc.)
     */
    record UploadResult(
        String storageKey,
        String publicUrl,
        Long fileSizeBytes,
        Integer durationSeconds,
        Integer widthPixels,
        Integer heightPixels,
        Map<String, String> metadata
    ) {}
}
