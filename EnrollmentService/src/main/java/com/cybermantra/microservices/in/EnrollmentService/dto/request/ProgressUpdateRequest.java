package com.cybermantra.microservices.in.EnrollmentService.dto.request;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ProgressUpdateRequest {

    @NotNull(message = "Lecture ID is required")
    private Long lectureId;

    @Min(value = 0, message = "Watch time cannot be negative")
    private Integer watchTimeSeconds = 0;

    @Min(value = 0, message = "Last position cannot be negative")
    private Integer lastPositionSeconds = 0;

    private Boolean markCompleted = false;
}