package com.mobisec.in.contentservice.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobisec.in.contentservice.domain.entity.OutboxEvent;
import com.mobisec.in.contentservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Writes domain events to the outbox table.
 *
 * This is called INSIDE a @Transactional method alongside the business
 * entity save. Both writes succeed or both roll back — atomicity guaranteed.
 *
 * The OutboxScheduler reads the table and publishes to RabbitMQ separately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper          objectMapper;

    /**
     * Serialize the event object to JSON and persist an OutboxEvent row.
     *
     * Must be called within an active @Transactional scope — the caller
     * (ContentService) already has one open.
     *
     * @param eventType  e.g. "ContentUploadedEvent"
     * @param routingKey RabbitMQ routing key
     * @param payload    The event POJO (will be serialized to JSON)
     */
    public void save(String eventType, String routingKey, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventType(eventType)
                    .routingKey(routingKey)
                    .payload(json)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.debug("Outbox event saved: type={}, routingKey={}", eventType, routingKey);

        } catch (Exception e) {
            // If serialization fails it's a programming error — throw immediately
            log.error("Failed to save outbox event: type={}", eventType, e);
            throw new RuntimeException("Failed to serialize outbox event: " + eventType, e);
        }
    }
}
