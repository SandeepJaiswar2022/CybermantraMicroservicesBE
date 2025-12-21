package com.mobisec.in.courseservice.dto.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitForReviewRequest {

    @NotBlank(message = "Submission message is required")
    @Size(min = 10, max = 500, message = "Message must be between 10 and 500 characters")
    private String message;
}