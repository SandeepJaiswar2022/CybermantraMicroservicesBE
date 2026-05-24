package com.mobisec.in.contentservice.repository;

import com.mobisec.in.contentservice.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetch a batch of unpublished events ordered by creation time.
     * LIMIT applied in the query to avoid loading all rows in memory.
     * The scheduler calls this every 500ms.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE published = false " +
                   "ORDER BY created_at ASC LIMIT :batchSize", nativeQuery = true)
    List<OutboxEvent> findUnpublishedBatch(@Param("batchSize") int batchSize);

    /**
     * Delete successfully published events older than retentionCutoff.
     * Called by the nightly cleanup job to keep the table small.
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.published = true AND o.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
