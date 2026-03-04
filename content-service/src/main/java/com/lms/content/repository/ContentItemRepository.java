package com.lms.content.repository;

import com.lms.content.domain.entity.ContentItem;
import com.lms.content.domain.enums.ContentStatus;
import com.lms.content.domain.enums.ContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Content item repository.
 *
 * <p>Key design choices:
 * <ul>
 *   <li>Named queries use JPQL (not native SQL) for portability and type safety.</li>
 *   <li>Projections ({@link ContentSummary}) avoid SELECT * on wide rows when only
 *       display metadata is needed — critical for list endpoints under high traffic.</li>
 *   <li>Bulk status update uses a single UPDATE query instead of loading entities
 *       (avoids N entity loads + N dirty checks).</li>
 * </ul>
 */
@Repository
public interface ContentItemRepository extends JpaRepository<ContentItem, UUID> {

    /**
     * Fetch all content for a lecture. Most common query — must be fast.
     * Filters out DELETED records at DB level.
     */
    @Query("SELECT c FROM ContentItem c WHERE c.lectureId = :lectureId AND c.status != 'DELETED' ORDER BY c.createdAt ASC")
    List<ContentItem> findByLectureId(@Param("lectureId") UUID lectureId);

    /**
     * Fetch all content for a course (e.g., to delete all when course is removed).
     */
    List<ContentItem> findByCourseIdAndStatusNot(UUID courseId, ContentStatus status);

    /**
     * Paginated query for instructor's content dashboard.
     */
    Page<ContentItem> findByInstructorIdAndContentTypeAndStatusNot(
        UUID instructorId,
        ContentType contentType,
        ContentStatus status,
        Pageable pageable
    );

    /**
     * Find single content item by ID, only if it belongs to the given lecture.
     * Prevents horizontal privilege escalation.
     */
    Optional<ContentItem> findByIdAndLectureId(UUID id, UUID lectureId);

    /**
     * Find single READY content item by ID — used for secure URL generation.
     */
    Optional<ContentItem> findByIdAndStatus(UUID id, ContentStatus status);

    /**
     * Projection query: only fetches id, originalFilename, contentType, mimeType, fileSizeBytes.
     * Use for list views where full entity data is overkill.
     */
    @Query("SELECT c.id as id, c.originalFilename as originalFilename, c.contentType as contentType, " +
           "c.mimeType as mimeType, c.fileSizeBytes as fileSizeBytes, c.status as status, " +
           "c.durationSeconds as durationSeconds, c.createdAt as createdAt " +
           "FROM ContentItem c WHERE c.lectureId = :lectureId AND c.status != 'DELETED'")
    List<ContentSummary> findSummariesByLectureId(@Param("lectureId") UUID lectureId);

    /**
     * Bulk status update — avoids loading entities just to change status.
     * Used by cleanup jobs and cascade deletes.
     */
    @Modifying
    @Query("UPDATE ContentItem c SET c.status = :status WHERE c.courseId = :courseId")
    int bulkUpdateStatusByCourseId(@Param("courseId") UUID courseId, @Param("status") ContentStatus status);

    /**
     * Count READY content items per lecture — useful for course stats.
     */
    @Query("SELECT COUNT(c) FROM ContentItem c WHERE c.lectureId = :lectureId AND c.status = 'READY'")
    long countReadyByLectureId(@Param("lectureId") UUID lectureId);

    /**
     * Check ownership without loading the full entity.
     */
    boolean existsByIdAndInstructorId(UUID id, UUID instructorId);

    // ─── Projection Interface ──────────────────────────────────────────────

    /**
     * Lightweight projection for list views.
     * Spring Data generates a proxy — no need to write a DTO for this.
     */
    interface ContentSummary {
        UUID getId();
        String getOriginalFilename();
        ContentType getContentType();
        String getMimeType();
        Long getFileSizeBytes();
        ContentStatus getStatus();
        Integer getDurationSeconds();
        java.time.Instant getCreatedAt();
    }
}
