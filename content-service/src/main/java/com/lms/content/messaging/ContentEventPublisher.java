package com.lms.content.messaging;

import com.lms.content.domain.entity.ContentItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Publishes content lifecycle events to RabbitMQ.
 *
 * <p>Event-driven design enables:
 * <ul>
 *   <li>Course Service to update lecture metadata (duration, size) without polling.</li>
 *   <li>Notification Service to alert instructors of upload completion.</li>
 *   <li>Future transcoding pipeline to pick up videos for HLS encoding.</li>
 *   <li>Analytics service to track content activity.</li>
 * </ul>
 *
 * <p>Events are published as Maps (JSON-serialized by Jackson via RabbitMQ config).
 * For a larger system, define dedicated event POJOs with versioning.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${messaging.exchange}")
    private String exchange;

    @Value("${messaging.routing-keys.content-uploaded}")
    private String uploadedRoutingKey;

    @Value("${messaging.routing-keys.content-deleted}")
    private String deletedRoutingKey;

    @Value("${messaging.routing-keys.transcode-request}")
    private String transcodeRequestRoutingKey;

    /**
     * Publishes a {@code content.event.uploaded} event after successful storage upload.
     *
     * <p>Consumers: Course Service (update lecture duration), Notification Service,
     * future Transcoding Service (picks up VIDEO type events).
     */
    public void publishContentUploaded(ContentItem item) {
        Map<String, Object> event = Map.of(
            "eventType",     "CONTENT_UPLOADED",
            "contentId",     item.getId().toString(),
            "lectureId",     item.getLectureId().toString(),
            "courseId",      item.getCourseId().toString(),
            "instructorId",  item.getInstructorId().toString(),
            "contentType",   item.getContentType().name(),
            "mimeType",      item.getMimeType(),
            "fileSizeBytes", item.getFileSizeBytes() != null ? item.getFileSizeBytes() : 0L,
            "durationSeconds", item.getDurationSeconds() != null ? item.getDurationSeconds() : 0
        );

        rabbitTemplate.convertAndSend(exchange, uploadedRoutingKey, event);
        log.info("Published CONTENT_UPLOADED event for contentId=[{}]", item.getId());
    }

    /**
     * Publishes a {@code content.event.deleted} event.
     *
     * <p>Consumers: Course Service (clear lecture video reference).
     */
    public void publishContentDeleted(ContentItem item) {
        Map<String, Object> event = Map.of(
            "eventType",   "CONTENT_DELETED",
            "contentId",   item.getId().toString(),
            "lectureId",   item.getLectureId().toString(),
            "courseId",    item.getCourseId().toString(),
            "contentType", item.getContentType().name()
        );

        rabbitTemplate.convertAndSend(exchange, deletedRoutingKey, event);
        log.info("Published CONTENT_DELETED event for contentId=[{}]", item.getId());
    }

    /**
     * Extension point: request video transcoding.
     *
     * <p>Currently not called; will be invoked from {@link com.lms.content.service.impl.ContentServiceImpl}
     * after video upload when transcoding pipeline is integrated.
     *
     * <p>A separate Transcoding Service will consume this event, run FFmpeg to generate
     * HLS segments, store them in MinIO/S3, and call back via transcode.complete.
     */
    public void publishTranscodeRequest(UUID contentId, String storageKey) {
        Map<String, Object> event = Map.of(
            "eventType",  "TRANSCODE_REQUEST",
            "contentId",  contentId.toString(),
            "storageKey", storageKey
        );

        rabbitTemplate.convertAndSend(exchange, transcodeRequestRoutingKey, event);
        log.info("Published TRANSCODE_REQUEST for contentId=[{}]", contentId);
    }
}
