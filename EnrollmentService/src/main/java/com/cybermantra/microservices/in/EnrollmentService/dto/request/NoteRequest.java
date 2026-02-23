package com.cybermantra.microservices.in.EnrollmentService.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class NoteRequest {

    @NotNull(message = "Lecture ID is required")
    private Long lectureId;

    @NotBlank(message = "Note text cannot be blank")
    private String noteText;

    private Integer timestampSeconds;
}
