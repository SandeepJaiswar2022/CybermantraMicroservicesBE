package com.lms.content.service.storage;

import com.lms.content.config.MinioProperties;
import com.lms.content.exception.StorageException;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO storage implementation (S3-compatible, self-hosted).
 *
 * <p><b>Why MinIO for no-AWS-budget setups?</b>
 * <ul>
 *   <li>Self-hosted: run on a single VPS — zero cloud storage cost.</li>
 *   <li>S3-compatible API: migrating to AWS S3 later requires only an endpoint change.</li>
 *   <li>Full byte-range support — essential for video seeking without proxy.</li>
 *   <li>Presigned URLs with expiry: clients download directly, no server bandwidth used.</li>
 *   <li>Multipart upload for large files without loading into memory.</li>
 * </ul>
 *
 * <p><b>Cons vs Cloudinary:</b>
 * <ul>
 *   <li>No built-in CDN (add Nginx or Cloudflare in front of MinIO).</li>
 *   <li>No automatic video transcoding (you'll need FFmpeg/transcoding service).</li>
 *   <li>Requires server management, disk provisioning, and backups.</li>
 * </ul>
 *
 * <p><b>Recommendation for your use case (no AWS budget):</b>
 * Use MinIO. A single VPS with 200GB SSD ($10–20/month) handles a growing LMS.
 * Add Cloudflare (free) in front for CDN. Later migrate to S3 with a config change.
 *
 * <p>Active when {@code content.storage.provider=minio}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "content.storage.provider", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    /**
     * Uploads a file to MinIO using streaming (no full file in memory).
     *
     * <p>The file is streamed directly from the multipart request to MinIO.
     * For files >5MB, MinIO client automatically uses multipart upload internally.
     * This keeps JVM heap usage flat regardless of file size.
     */
    @Override
    public UploadResult upload(MultipartFile file, String folder, String resourceType) {
        String bucket = resolveBucket(resourceType);
        String objectKey = folder + "/" + UUID.randomUUID() + "/" + sanitize(file.getOriginalFilename());

        log.info("Uploading [{}] to MinIO bucket=[{}] key=[{}]", file.getOriginalFilename(), bucket, objectKey);

        try (InputStream inputStream = file.getInputStream()) {
            // Stream directly to MinIO — no intermediate byte[] allocation
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)  // -1 = use multipart auto
                    .contentType(file.getContentType())
                    .build()
            );

            // Retrieve object stats for metadata
            StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder().bucket(bucket).object(objectKey).build()
            );

            log.info("MinIO upload successful: key=[{}], size=[{}]", objectKey, stat.size());

            return new UploadResult(
                objectKey,
                // Public URL (only usable if bucket is public — use presigned for private)
                minioProperties.getEndpoint() + "/" + bucket + "/" + objectKey,
                stat.size(),
                null,  // MinIO doesn't extract duration — do it with FFprobe in post-processing
                null,
                null,
                Map.of("etag", stat.etag(), "bucket", bucket)
            );

        } catch (Exception e) {
            log.error("MinIO upload failed for key=[{}]: {}", objectKey, e.getMessage(), e);
            throw new StorageException("MinIO upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a presigned GET URL valid for the configured TTL.
     *
     * <p>The presigned URL is signed with AWS Signature V4, meaning:
     * <ul>
     *   <li>URL is time-limited (expires after TTL).</li>
     *   <li>URL is scoped to a single object — cannot access other keys.</li>
     *   <li>Supports HTTP byte-range requests — video player seeking works natively.</li>
     * </ul>
     */
    @Override
    public String generateSignedUrl(String storageKey, String resourceType, long expirySeconds) {
        String bucket = resolveBucket(resourceType);
        log.debug("Generating presigned URL for bucket=[{}] key=[{}] expiry=[{}s]", bucket, storageKey, expirySeconds);

        try {
            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(storageKey)
                    .expiry((int) expirySeconds, TimeUnit.SECONDS)
                    .build()
            );
            log.debug("Presigned URL generated for key=[{}]", storageKey);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for key=[{}]: {}", storageKey, e.getMessage(), e);
            throw new StorageException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes an object from MinIO.
     */
    @Override
    public void delete(String storageKey, String resourceType) {
        String bucket = resolveBucket(resourceType);
        log.info("Deleting MinIO object: bucket=[{}] key=[{}]", bucket, storageKey);
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build()
            );
            log.info("Deleted MinIO object: [{}]", storageKey);
        } catch (Exception e) {
            log.error("Failed to delete MinIO object [{}]: {}", storageKey, e.getMessage(), e);
            throw new StorageException("Failed to delete from MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Returns an InputStream for proxy streaming.
     *
     * <p>Use this ONLY when presigned URLs cannot be used (e.g., internal networks).
     * For public-facing video, always use presigned URLs + HTTP redirect to avoid
     * routing all video traffic through this service.
     *
     * <p>MinIO supports byte-range requests natively — the client can pass
     * {@code Range} headers which MinIO handles without loading the whole file.
     */
    @Override
    public InputStream streamContent(String storageKey) {
        // Determine bucket from key prefix convention (e.g., "videos/..." → videos bucket)
        String bucket = resolveFromKey(storageKey);
        log.debug("Opening stream for MinIO key=[{}] bucket=[{}]", storageKey, bucket);
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to stream MinIO object [{}]: {}", storageKey, e.getMessage(), e);
            throw new StorageException("Failed to stream content: " + e.getMessage(), e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String resolveBucket(String resourceType) {
        return switch (resourceType) {
            case "video" -> minioProperties.getBucket().getVideos();
            case "image" -> minioProperties.getBucket().getThumbnails();
            default      -> minioProperties.getBucket().getResources();
        };
    }

    private String resolveFromKey(String storageKey) {
        if (storageKey.startsWith("videos/"))     return minioProperties.getBucket().getVideos();
        if (storageKey.startsWith("thumbnails/")) return minioProperties.getBucket().getThumbnails();
        return minioProperties.getBucket().getResources();
    }

    private String sanitize(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }
}
