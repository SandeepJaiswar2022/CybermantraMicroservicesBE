package com.mobisec.in.contentservice.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when content is soft-deleted.
 * Routing key: content.event.deleted
 * Consumers: Search Service (remove from index), CDN cache invalidation
 */
@Data
@Builder
public class ContentDeletedEvent {

    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    private UUID    contentId;
    private UUID    lectureId;
    private UUID    courseId;
    private String  storageKey;
    private Instant occurredAt;
}
