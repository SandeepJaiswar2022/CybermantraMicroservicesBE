package com.mobisec.in.contentservice.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when content reaches READY status (transcoding complete).
 * Routing key: content.event.ready
 * Consumers: Notification Service, Course Service
 */
@Data
@Builder
public class ContentReadyEvent {

    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    private UUID    contentId;
    private UUID    lectureId;
    private UUID    courseId;
    private String  contentType;
    private Integer durationSeconds;
    private String  hlsManifestKey;
    private Instant occurredAt;
}
