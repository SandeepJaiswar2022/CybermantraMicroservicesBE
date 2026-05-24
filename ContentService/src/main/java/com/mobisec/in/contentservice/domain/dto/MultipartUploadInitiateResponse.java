package com.mobisec.in.contentservice.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Returned for files AT OR ABOVE the multipart threshold (>= 100 MB).
 *
 * Client flow:
 *  1. Split the file into chunks of partSizeBytes
 *  2. For each part:  HTTP PUT parts[i].presignedUrl  { body = chunk bytes }
 *                     capture ETag from MinIO response header
 *  3. POST /api/v1/content/{contentId}/complete-multipart
 *         { uploadId, parts: [{partNumber, eTag}, ...] }
 *
 * Parts can be uploaded in parallel for speed.
 * If one part fails, only that part needs to be retried.
 */
public record MultipartUploadInitiateResponse(
        UUID   contentId,
        String uploadId,          // MinIO multipart uploadId — keep this, needed for complete
        List<PartPresignedUrl> parts,
        long   partSizeBytes,     // How large each chunk should be
        int    totalParts,
        Instant presignedUrlExpiresAt,
        String status,
        boolean multipart         // always true here
) {
    public record PartPresignedUrl(
            int    partNumber,    // 1-indexed
            String presignedUrl
    ) {}
}
