package com.mobisec.in.contentservice.storage;

import java.time.Duration;
import java.util.List;

/**
 * Abstraction over object storage.
 * Business logic depends on this interface, never on MinIO classes directly.
 * Swap MinIO for S3/GCS by providing a different implementation bean.
 */
public interface StorageService {

    // ── Single-part operations ────────────────────────────────────────────────

    /**
     * Generate a presigned PUT URL for a single-part upload.
     * The client PUTs the file bytes directly to this URL — no backend involvement.
     */
    String generatePresignedUploadUrl(String storageKey, String mimeType, Duration expiry);

    /**
     * Generate a presigned GET URL for downloading/streaming.
     * Used when a client needs to play back or download content.
     */
    String generatePresignedDownloadUrl(String storageKey, Duration expiry);

    /**
     * Get metadata about an object without downloading it.
     * Throws StorageException if the object does not exist.
     */
    StorageObjectMetadata statObject(String storageKey);

    /**
     * Delete an object from storage.
     * Idempotent — deleting a non-existent key does not throw.
     */
    void deleteObject(String storageKey);

    // ── Multipart operations ──────────────────────────────────────────────────

    /**
     * Initiate a multipart upload.
     * Returns an uploadId that must be passed to all subsequent part operations.
     */
    String initiateMultipartUpload(String storageKey, String mimeType);

    /**
     * Generate a presigned PUT URL for a single part of a multipart upload.
     *
     * @param storageKey  The final object key
     * @param uploadId    From initiateMultipartUpload()
     * @param partNumber  1-indexed part number
     * @param expiry      How long the URL is valid
     */
    String generatePresignedPartUrl(String storageKey, String uploadId,
                                    int partNumber, Duration expiry);

    /**
     * Complete a multipart upload — tells MinIO to assemble all parts into
     * the final object. Parts must be provided in ascending partNumber order.
     *
     * @param parts List of completed parts with their ETags
     */
    void completeMultipartUpload(String storageKey, String uploadId,
                                 List<CompletedPart> parts);

    /**
     * Abort a multipart upload — releases all temporary part storage.
     * Called when an upload fails or is abandoned.
     */
    void abortMultipartUpload(String storageKey, String uploadId);

    // ── Value objects ─────────────────────────────────────────────────────────

    record StorageObjectMetadata(
            String storageKey,
            long   sizeBytes,
            String etag,
            String contentType
    ) {}

    record CompletedPart(int partNumber, String etag) {}
}
