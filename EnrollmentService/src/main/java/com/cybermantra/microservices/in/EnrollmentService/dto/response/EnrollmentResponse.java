package com.cybermantra.microservices.in.EnrollmentService.dto.response;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EnrollmentResponse {
    private Long id;
    private UUID userId;
    private Long courseId;
    private LocalDateTime enrollmentDate;
    private LocalDateTime completionDate;
    private Boolean isCompleted;
    private BigDecimal progressPercentage;
    private LocalDateTime lastAccessedAt;
    private boolean hasCertificate;
}