package com.mobisec.in.courseservice.dto.course;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewCourseRequest {

    @NotBlank(message = "Decision is required")
    private String decision; // APPROVE or REJECT

    @Size(max = 1000, message = "Feedback cannot exceed 1000 characters")
    private String feedback;
}