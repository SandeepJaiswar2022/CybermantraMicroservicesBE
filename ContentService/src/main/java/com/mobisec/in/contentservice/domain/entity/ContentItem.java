package com.mobisec.in.contentservice.domain.entity;

import com.mobisec.in.contentservice.domain.enums.ContentStatus;
import com.mobisec.in.contentservice.domain.enums.ContentType;
import com.mobisec.in.contentservice.domain.enums.StorageProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "content_items",
        indexes = {
                @Index(name = "idx_content_lecture_id",     columnList = "lecture_id"),
                @Index(name = "idx_content_course_id",      columnList = "course_id"),
                @Index(name = "idx_content_instructor_id",  columnList = "instructor_id"),
                @Index(name = "idx_content_type_status",    columnList = "content_type, status"),
                @Index(name = "idx_content_status_created", columnList = "status, created_at")
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

    // ── Ownership ────────────────────────────────────────────────────────────

    /** Logical FK to lecture in Course Service — no join enforced. */
    @Column(name = "lecture_id", nullable = false)
    private UUID lectureId;

    /** Denormalized from lecture for efficient queries. */
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** Instructor who uploaded — used for ownership checks in every mutation. */
    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    // ── Storage ──────────────────────────────────────────────────────────────

    /**
     * MinIO object key, e.g. "lectures/{lectureId}/VIDEO/{uuid}.mp4".
     * NEVER returned in API responses — always issue a presigned download URL.
     */
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "storage_provider", columnDefinition = "storage_provider_enum")
    private StorageProvider storageProvider;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "content_type", columnDefinition = "content_type_enum")
    private ContentType contentType;

    // ── File metadata ────────────────────────────────────────────────────────

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /**
     * Initially the client-declared MIME type.
     * Re-verified by Apache Tika after upload completes.
     */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** Authoritative size from MinIO statObject — overrides client-reported value. */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Null until transcoding service populates it. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "width_pixels")
    private Integer widthPixels;

    @Column(name = "height_pixels")
    private Integer heightPixels;

    /** HLS master playlist key — null until transcoding completes. */
    @Column(name = "hls_manifest_key", length = 512)
    private String hlsManifestKey;

    // ── Multipart tracking ───────────────────────────────────────────────────

    /**
     * MinIO multipart uploadId — set when multipart is initiated.
     * NULL means single-part upload was used (file < 100MB threshold).
     */
    @Column(name = "minio_upload_id", length = 255)
    private String minioUploadId;

    // ── Lifecycle ────────────────────────────────────────────────────────────

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

    // ── Domain helpers ───────────────────────────────────────────────────────

    public boolean isOwner(UUID userId) {
        return this.instructorId.equals(userId);
    }

    public boolean isMultipart() {
        return this.minioUploadId != null && !this.minioUploadId.isBlank();
    }

    public void markProcessing() { this.status = ContentStatus.PROCESSING; }
    public void markReady()      { this.status = ContentStatus.READY; }
    public void markFailed()     { this.status = ContentStatus.FAILED; }
    public void markDeleted()    { this.status = ContentStatus.DELETED; }
}
