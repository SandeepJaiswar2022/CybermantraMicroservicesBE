package com.mobisec.in.courseservice.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mobisec.in.courseservice.enums.CourseLevel;
import com.mobisec.in.courseservice.enums.CourseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "courses", indexes = {
        @Index(name = "idx_course_instructor_id", columnList = "instructor_id"),
        @Index(name = "idx_course_category_id", columnList = "category_id"),
        @Index(name = "idx_course_subcategory_id", columnList = "subcategory_id"),
        @Index(name = "idx_course_status", columnList = "status"),
        @Index(name = "idx_course_is_published", columnList = "is_published"),
        @Index(name = "idx_course_is_deleted", columnList = "is_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId; // Reference to User Service

    private String instructorName;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "subtitle", length = 500)
    private String subtitle;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @JsonIgnoreProperties({"courses", "subcategories", "parent"})
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private Category subcategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 50)
    private CourseLevel level;

    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "en";

    @Column(name = "price", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "promo_video_url", length = 500)
    private String promoVideoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    @Builder.Default
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "target_audience", columnDefinition = "TEXT")
    private String targetAudience;

    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;

    // Calculated field - sum of all lecture durations
    @Column(name = "total_duration_seconds")
    @Builder.Default
    private Integer totalDurationSeconds = 0;

    // Calculated field - total number of lectures
    @Column(name = "total_lectures")
    @Builder.Default
    private Integer totalLectures = 0;

    // Calculated field - total number of sections
    @Column(name = "total_sections")
    @Builder.Default
    private Integer totalSections = 0;

    // Calculated field - average rating
    @Column(name = "average_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    // Calculated field - total number of ratings
    @Column(name = "total_ratings")
    @Builder.Default
    private Integer totalRatings = 0;

    // Calculated field - total enrollments
    @Column(name = "total_enrollments")
    @Builder.Default
    private Integer totalEnrollments = 0;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    @JsonIgnore
    private List<CourseSection> sections = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Helper methods
    public void addSection(CourseSection section) {
        sections.add(section);
        section.setCourse(this);
    }

    public void removeSection(CourseSection section) {
        sections.remove(section);
        section.setCourse(null);
    }
}