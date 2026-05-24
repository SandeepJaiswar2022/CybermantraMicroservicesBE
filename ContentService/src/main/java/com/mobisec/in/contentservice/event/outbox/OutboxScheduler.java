package com.mobisec.in.contentservice.event.outbox;

import com.mobisec.in.contentservice.config.RabbitMQConfig;
import com.mobisec.in.contentservice.domain.entity.OutboxEvent;
import com.mobisec.in.contentservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Outbox Scheduler — the second half of the Transactional Outbox Pattern.
 *
 * Every 500ms this polls the outbox_events table for unpublished rows,
 * attempts to publish each to RabbitMQ, and marks them published on success.
 *
 * Why this is safe:
 * - If RabbitMQ is down, events accumulate in DB and are retried next poll.
 * - If the scheduler crashes mid-batch, unpublished rows are retried on restart.
 * - Consumers must be idempotent (eventId deduplication) because a crash
 *   between "publish success" and "mark published" can cause a re-publish.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate        rabbitTemplate;

    @Value("${outbox.scheduler.batch-size:50}")
    private int batchSize;

    @Value("${outbox.cleanup.retention-days:7}")
    private int retentionDays;

    /**
     * Main polling loop — runs every 500ms.
     *
     * Each event is processed individually so a single bad event
     * (e.g. poison message) does not block the rest of the batch.
     *
     * @Transactional here means: the mark-as-published DB update is
     * committed once per event, not once for the whole batch. This
     * minimises the re-delivery window on crash.
     */
    @Scheduled(fixedDelayString = "${outbox.scheduler.fixed-delay-ms:500}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findUnpublishedBatch(batchSize);

        if (pending.isEmpty()) return;

        log.debug("Outbox: processing {} pending events", pending.size());

        for (OutboxEvent event : pending) {
            publishSingle(event);
        }
    }

    @Transactional
    public void publishSingle(OutboxEvent event) {
        try {
            // Send to RabbitMQ — convertAndSend serializes using Jackson
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CONTENT_EVENTS_EXCHANGE,
                    event.getRoutingKey(),
                    event.getPayload()     // already serialized JSON string
            );

            // Mark published — this DB write is committed when the method returns
            event.setPublished(true);
            event.setPublishedAt(Instant.now());
            outboxEventRepository.save(event);

            log.info("Outbox published: id={}, type={}, routingKey={}",
                    event.getId(), event.getEventType(), event.getRoutingKey());

        } catch (Exception e) {
            // RabbitMQ is unavailable or delivery failed.
            // Increment retry count and store the error for diagnostics.
            // The event will be retried on the next poll.
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastError(e.getMessage());
            outboxEventRepository.save(event);

            log.warn("Outbox publish failed (attempt {}): id={}, error={}",
                    event.getRetryCount(), event.getId(), e.getMessage());
        }
    }

    /**
     * Nightly cleanup — deletes published events older than retention period.
     * Keeps the outbox table from growing unboundedly.
     */
    @Scheduled(cron = "${outbox.cleanup.cron:0 0 2 * * *}")
    @Transactional
    public void cleanupPublishedEvents() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = outboxEventRepository.deletePublishedBefore(cutoff);
        log.info("Outbox cleanup: deleted {} published events older than {} days",
                deleted, retentionDays);
    }
}
