package com.mobisec.in.contentservice.repository;

import com.mobisec.in.contentservice.domain.entity.ContentItem;
import com.mobisec.in.contentservice.domain.enums.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ContentItemRepository extends JpaRepository<ContentItem, UUID> {

    List<ContentItem> findByLectureId(UUID lectureId);

    List<ContentItem> findByCourseIdAndStatus(UUID courseId, ContentStatus status);

    List<ContentItem> findByInstructorId(UUID instructorId);

    /**
     * Used by the cleanup scheduler to find abandoned UPLOADING records.
     * Any record stuck in UPLOADING longer than the threshold is considered
     * abandoned — the client never called /complete.
     */
    @Query("SELECT c FROM ContentItem c WHERE c.status = 'UPLOADING' AND c.createdAt < :cutoff")
    List<ContentItem> findAbandonedUploads(@Param("cutoff") Instant cutoff);
}
