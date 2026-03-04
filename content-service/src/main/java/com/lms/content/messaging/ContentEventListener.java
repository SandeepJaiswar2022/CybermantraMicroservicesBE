package com.lms.content.messaging;

import com.lms.content.service.ContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes RabbitMQ events targeted at the Content Service.
 *
 * <p>Currently handles:
 * <ul>
 *   <li>{@code content.transcode.complete} — fired by the future Transcoding Service
 *       when HLS encoding of a video is done.</li>
 * </ul>
 *
 * <p>Messages are acknowledged manually ({@code acknowledge-mode: manual} in config),
 * so a failed handler won't silently discard the message — it stays in the queue
 * for redelivery or DLQ routing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ContentEventListener {

    private final ContentService contentService;

    /**
     * Handles transcode-complete events from the video processing service.
     *
     * <p>Extension point: once the transcoding service is built, it publishes to
     * {@code content.transcode.complete} with the contentId and HLS manifest key.
     * This listener routes the callback into the service layer.
     */
    @RabbitListener(queues = "${messaging.queues.transcode-complete}")
    public void onTranscodeComplete(Map<String, Object> event, Message message) {
        log.info("Received TRANSCODE_COMPLETE event: {}", event);
        try {
            UUID contentId    = UUID.fromString((String) event.get("contentId"));
            String hlsKey     = (String) event.get("hlsManifestKey");

            contentService.onTranscodeComplete(contentId, hlsKey);
            log.info("Transcode-complete processed for contentId=[{}]", contentId);

        } catch (Exception e) {
            log.error("Failed to process TRANSCODE_COMPLETE event: {}", event, e);
            // Rethrow to trigger manual NACK (message goes to DLQ or gets requeued)
            throw new RuntimeException("Event processing failed", e);
        }
    }
}
