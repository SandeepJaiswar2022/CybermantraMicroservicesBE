package com.mobisec.in.contentservice.domain.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of a ContentItem.
 * storageKey is intentionally excluded — use a dedicated download-URL endpoint.
 */
public record ContentStatusResponse(
        UUID    id,
        UUID    lectureId,
        UUID    courseId,
        String  contentType,
        String  status,
        String  originalFilename,
        String  mimeType,
        Long    fileSizeBytes,
        Integer durationSeconds,
        Integer widthPixels,
        Integer heightPixels,
        String  hlsManifestKey,
        boolean multipart,
        Instant createdAt,
        Instant updatedAt
) {}
