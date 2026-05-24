package com.mobisec.in.contentservice.domain.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned for files BELOW the multipart threshold (< 100 MB).
 *
 * Client flow:
 *  1. HTTP PUT presignedUploadUrl  { body = raw file bytes }
 *  2. Capture ETag from MinIO response header
 *  3. POST /api/v1/content/{contentId}/complete  { eTag }
 */
public record UploadInitiateResponse(
        UUID    contentId,
        String  presignedUploadUrl,
        Instant presignedUrlExpiresAt,
        String  status,
        boolean multipart   // always false here
) {}
