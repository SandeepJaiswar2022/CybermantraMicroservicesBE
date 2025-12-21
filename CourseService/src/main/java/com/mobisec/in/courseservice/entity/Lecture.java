package com.mobisec.in.courseservice.entity;


import com.mobisec.in.courseservice.enums.LectureContentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lectures", indexes = {
        @Index(name = "idx_lecture_section_id", columnList = "section_id"),
        @Index(name = "idx_lecture_order_index", columnList = "order_index"),
        @Index(name = "idx_lecture_is_preview", columnList = "is_preview")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lecture {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private CourseSection section;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "is_preview", nullable = false)
    @Builder.Default
    private Boolean isPreview = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", length = 50)
    @Builder.Default
    private LectureContentType contentType = LectureContentType.VIDEO;

    @Column(name = "article_content", columnDefinition = "TEXT")
    private String articleContent; // For text-based lectures

    @Column(name = "resource_urls", columnDefinition = "TEXT")
    private String resourceUrls; // JSON array of downloadable resources

    @Column(name = "is_completed_by_instructor", nullable = false)
    @Builder.Default
    private Boolean isCompletedByInstructor = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
