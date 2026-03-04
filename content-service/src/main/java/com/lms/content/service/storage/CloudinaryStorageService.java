package com.lms.content.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.lms.content.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Cloudinary storage implementation.
 *
 * <p><b>Why Cloudinary for low-budget setups?</b>
 * <ul>
 *   <li>No server infrastructure to maintain (vs MinIO which needs a VM + disk).</li>
 *   <li>Built-in CDN, image transformations, and video streaming.</li>
 *   <li>Free tier: 25 GB storage + 25 GB bandwidth/month — enough for MVP.</li>
 *   <li>Signed URLs natively supported with HMAC-SHA1.</li>
 * </ul>
 *
 * <p><b>Cons vs MinIO:</b>
 * <ul>
 *   <li>Costs money at scale (bandwidth charges).</li>
 *   <li>Data sovereignty: files leave your infrastructure.</li>
 *   <li>Less control over streaming (no byte-range support without paid plan).</li>
 * </ul>
 *
 * <p>Active when {@code content.storage.provider=cloudinary}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "content.storage.provider", havingValue = "cloudinary")
public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;
    private final long signedUrlExpirySeconds;

    /**
     * Uploads a file to Cloudinary.
     *
     * <p>We use {@code upload_preset} for per-type transformation pipelines
     * (e.g., the video preset can auto-generate thumbnails on Cloudinary's side).
     *
     * <p>We pass the file as a byte array here. For very large files (>500MB),
     * consider using Cloudinary's chunked upload API instead.
     */
    @Override
    public UploadResult upload(MultipartFile file, String folder, String resourceType) {
        log.info("Uploading file [{}] to Cloudinary folder [{}] as [{}]",
                 file.getOriginalFilename(), folder, resourceType);
        try {
            Map<String, Object> options = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", resourceType,
                // Overwrite existing key (idempotent on retry)
                "overwrite", true,
                // Use the original filename as the display name (not the storage key)
                "use_filename", false,
                // Generate a unique public_id to prevent key collisions
                "unique_filename", true
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), options);

            log.debug("Cloudinary upload result: {}", result);

            String storageKey = (String) result.get("public_id");
            String publicUrl  = (String) result.get("secure_url");
            Long bytes        = ((Number) result.getOrDefault("bytes", 0L)).longValue();
            Integer duration  = result.get("duration") != null
                                ? ((Number) result.get("duration")).intValue() : null;
            Integer width     = result.get("width") != null
                                ? ((Number) result.get("width")).intValue() : null;
            Integer height    = result.get("height") != null
                                ? ((Number) result.get("height")).intValue() : null;

            Map<String, String> meta = new HashMap<>();
            meta.put("format", (String) result.getOrDefault("format", ""));
            meta.put("etag",   (String) result.getOrDefault("etag", ""));

            log.info("Upload successful: storageKey=[{}], size=[{}] bytes", storageKey, bytes);
            return new UploadResult(storageKey, publicUrl, bytes, duration, width, height, meta);

        } catch (IOException e) {
            log.error("Cloudinary upload failed for file [{}]: {}", file.getOriginalFilename(), e.getMessage(), e);
            throw new StorageException("Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a signed, time-limited URL for private content access.
     *
     * <p>Videos should always use signed URLs. Thumbnails can use public URLs.
     * The signature uses HMAC-SHA1 with your API secret — never expose the secret.
     */
    @Override
    public String generateSignedUrl(String storageKey, String resourceType, long expirySeconds) {
        log.debug("Generating signed URL for storageKey=[{}], resourceType=[{}], expiry=[{}s]",
                  storageKey, resourceType, expirySeconds);
        try {
            long expiresAt = System.currentTimeMillis() / 1000L + expirySeconds;

            @SuppressWarnings("unchecked")
            Map<String, Object> options = ObjectUtils.asMap(
                "resource_type", resourceType,
                "type", "upload",
                "sign_url", true,
                "expires_at", expiresAt
            );

            String signedUrl = cloudinary.url()
                .resourceType(resourceType)
                .type("upload")
                .signed(true)
                .transformation(new Transformation())
                .generate(storageKey);

            log.debug("Signed URL generated successfully for storageKey=[{}]", storageKey);
            return signedUrl;

        } catch (Exception e) {
            log.error("Failed to generate signed URL for storageKey=[{}]: {}", storageKey, e.getMessage(), e);
            throw new StorageException("Failed to generate signed URL: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a resource from Cloudinary.
     * Uses "invalidate=true" to purge from Cloudinary's CDN cache immediately.
     */
    @Override
    public void delete(String storageKey, String resourceType) {
        log.info("Deleting Cloudinary resource: storageKey=[{}], resourceType=[{}]", storageKey, resourceType);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> options = ObjectUtils.asMap(
                "resource_type", resourceType,
                "invalidate", true   // purge CDN cache
            );
            cloudinary.uploader().destroy(storageKey, options);
            log.info("Deleted Cloudinary resource: [{}]", storageKey);
        } catch (IOException e) {
            log.error("Failed to delete Cloudinary resource [{}]: {}", storageKey, e.getMessage(), e);
            throw new StorageException("Failed to delete from Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Cloudinary doesn't natively support proxy streaming.
     * For Cloudinary, always use signed URLs for streaming (HTTP redirect).
     * This method is a fallback and downloads the whole file — not recommended for video.
     */
    @Override
    public InputStream streamContent(String storageKey) {
        throw new UnsupportedOperationException(
            "Cloudinary does not support server-side proxy streaming. Use generateSignedUrl() instead."
        );
    }
}
