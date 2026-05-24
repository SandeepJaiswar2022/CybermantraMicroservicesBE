package com.mobisec.in.contentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox record.
 *
 * Written to this table IN THE SAME DB transaction as the business update.
 * A scheduler reads rows where published=false and sends them to RabbitMQ.
 *
 * Guarantee: ContentItem status change and the outbox row are atomic.
 * RabbitMQ delivery is best-effort with retries — never silent loss.
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_unpublished", columnList = "published, created_at"),
                @Index(name = "idx_outbox_cleanup",     columnList = "published_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** e.g. "ContentUploadedEvent" — used for routing and consumer logic */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** RabbitMQ routing key e.g. "content.event.uploaded" */
    @Column(name = "routing_key", nullable = false, length = 200)
    private String routingKey;

    /** Full event body as JSON string */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "published", nullable = false)
    @Builder.Default
    private boolean published = false;

    /** Incremented on each failed publish attempt */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /** Last failure message — useful for diagnosing stuck events */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
