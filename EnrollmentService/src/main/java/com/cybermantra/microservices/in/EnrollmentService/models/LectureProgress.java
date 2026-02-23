package com.cybermantra.microservices.in.EnrollmentService.models;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lecture_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"enrollment_id", "lecture_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LectureProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(name = "lecture_id", nullable = false)
    private Long lectureId;

    @Column(name = "is_completed")
    @Builder.Default
    private Boolean isCompleted = false;

    @Column(name = "watch_time_seconds")
    @Builder.Default
    private Integer watchTimeSeconds = 0;

    @Column(name = "last_position_seconds")
    @Builder.Default
    private Integer lastPositionSeconds = 0;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
