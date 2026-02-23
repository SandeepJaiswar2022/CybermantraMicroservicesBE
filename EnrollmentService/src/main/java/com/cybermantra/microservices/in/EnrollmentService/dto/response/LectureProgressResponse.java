package com.cybermantra.microservices.in.EnrollmentService.dto.response;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LectureProgressResponse {
    private Long id;
    private Long lectureId;
    private Boolean isCompleted;
    private Integer watchTimeSeconds;
    private Integer lastPositionSeconds;
    private LocalDateTime completedAt;
}