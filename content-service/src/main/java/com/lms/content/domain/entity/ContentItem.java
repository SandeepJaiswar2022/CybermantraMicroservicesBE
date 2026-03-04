package com.lms.content.domain.entity;

import com.lms.content.domain.enums.ContentStatus;
import com.lms.content.domain.enums.ContentType;
import com.lms.content.domain.enums.StorageProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Central entity representing any piece of content (video, resource, thumbnail).
 *
 * <p>Design notes:
 * <ul>
 *   <li>We store only metadata; actual bytes live in Cloudinary or MinIO.</li>
 *   <li>{@code storageKey} is the provider-specific identifier (public_id for Cloudinary,
 *       object key for MinIO). Never expose this directly in API responses.</li>
 *   <li>Soft-delete via {@code status = DELETED} so we can audit and defer storage cleanup.</li>
 *   <li>Indexed on {@code lectureId} and {@code courseId} to avoid full-table scans
 *       when fetching content for a lecture/course.</li>
 * </ul>
 */
@Entity
@Table(
    name = "content_items",
    indexes = {
        @Index(name = "idx_content_lecture_id",  columnList = "lecture_id"),
        @Index(name = "idx_content_course_id",   columnList = "course_id"),
        @Index(name = "idx_content_instructor",  columnList = "instructor_id"),
        @Index(name = "idx_content_type_status", columnList = "content_type, status"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // ─── Ownership / association ──────────────────────────────────────────

    /** ID of the lecture this content belongs to (FK lives in Course Service). */
    @Column(name = "lecture_id", nullable = false)
    private UUID lectureId;

    /** Denormalized for efficient queries without cross-service joins. */
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** User ID of the instructor who uploaded this content. */
    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    // ─── Storage metadata ────────────────────────────────────────────────

    /**
     * Provider-specific storage identifier.
     * Cloudinary: public_id (e.g., "lms/videos/abc123")
     * MinIO: object key (e.g., "lectures/uuid/video.mp4")
     * NOT exposed in API responses — always generate a signed URL instead.
     */
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 20)
    private StorageProvider storageProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    // ─── File metadata ────────────────────────────────────────────────────

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /** Detected MIME type (via Apache Tika), not blindly trusted from client. */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** File size in bytes. */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Video/audio duration in seconds (null for non-video). */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** Video width in pixels (null for non-video). */
    @Column(name = "width_pixels")
    private Integer widthPixels;

    /** Video height in pixels (null for non-video). */
    @Column(name = "height_pixels")
    private Integer heightPixels;

    // ─── HLS Extension Point ──────────────────────────────────────────────
    /**
     * Once we add a transcoding service, this will store the HLS master playlist key.
     * Null until transcoding is implemented.
     */
    @Column(name = "hls_manifest_key", length = 512)
    private String hlsManifestKey;

    // ─── Lifecycle ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ContentStatus status = ContentStatus.UPLOADING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ─── Helpers ─────────────────────────────────────────────────────────

    public boolean isOwner(UUID userId) {
        return this.instructorId.equals(userId);
    }

    public boolean isReady() {
        return this.status == ContentStatus.READY;
    }

    public void markReady() {
        this.status = ContentStatus.READY;
    }

    public void markFailed() {
        this.status = ContentStatus.FAILED;
    }

    public void markDeleted() {
        this.status = ContentStatus.DELETED;
    }
}
