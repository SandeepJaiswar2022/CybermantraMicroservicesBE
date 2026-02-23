package com.cybermantra.microservices.in.EnrollmentService.dto.response;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EnrollmentResponse {
    private Long id;
    private Long userId;
    private Long courseId;
    private LocalDateTime enrollmentDate;
    private LocalDateTime completionDate;
    private Boolean isCompleted;
    private BigDecimal progressPercentage;
    private LocalDateTime lastAccessedAt;
    private boolean hasCertificate;
}