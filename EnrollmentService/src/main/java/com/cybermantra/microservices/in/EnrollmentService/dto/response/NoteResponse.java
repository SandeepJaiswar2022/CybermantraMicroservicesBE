package com.cybermantra.microservices.in.EnrollmentService.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NoteResponse {
    private Long id;
    private Long lectureId;
    private String noteText;
    private Integer timestampSeconds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}