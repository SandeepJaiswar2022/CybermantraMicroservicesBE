package com.mobisec.in.contentservice.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a file has been confirmed in MinIO and is ready for processing.
 * Routing key: content.event.uploaded
 *
 * Consumers: Transcoding Service, Search Service
 *
 * Contains everything consumers need — they must NOT need to call back to
 * Content Service for more information.
 */
@Data
@Builder
public class ContentUploadedEvent {

    /** Unique event ID — consumers use this for idempotency checks */
    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    private UUID contentId;
    private UUID lectureId;
    private UUID courseId;
    private UUID instructorId;

    /**
     * MinIO storage key — safe inside internal RabbitMQ (not a public API).
     * Transcoding Service needs this to fetch the file from MinIO.
     */
    private String storageKey;

    private String contentType;   // VIDEO | RESOURCE | THUMBNAIL
    private String mimeType;
    private Long   fileSizeBytes;
    private boolean multipart;

    private Instant occurredAt;
}
