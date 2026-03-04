package com.lms.content.dto.response;

import com.lms.content.domain.enums.ContentStatus;
import com.lms.content.domain.enums.ContentType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * API response DTO for content item metadata.
 *
 * <p>NOTE: storageKey is NEVER included here.
 * Clients get a signed URL instead — they never know the internal storage location.
 */
@Data
@Builder
public class ContentItemResponse {

    private UUID id;
    private UUID lectureId;
    private UUID courseId;
    private UUID instructorId;

    private ContentType contentType;
    private ContentStatus status;
    private String originalFilename;
    private String mimeType;
    private Long fileSizeBytes;
    private Integer durationSeconds;
    private Integer widthPixels;
    private Integer heightPixels;

    /** Time-limited signed URL for accessing the content. May be null if not yet generated. */
    private String signedUrl;
    /** URL expiry epoch seconds (so client knows when to refresh). */
    private Long signedUrlExpiresAt;

    private Instant createdAt;
    private Instant updatedAt;
}
