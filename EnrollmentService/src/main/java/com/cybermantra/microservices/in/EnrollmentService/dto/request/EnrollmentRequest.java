package com.cybermantra.microservices.in.EnrollmentService.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EnrollmentRequest {

    @NotNull(message = "Course ID is required")
    private Long courseId;

    // Optional: for gifting/transfer
    private UUID targetUserId;
}