package com.mobisec.in.courseservice.dto.course;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewCourseRequest {

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "APPROVED|REJECTED", message = "Action must be APPROVED or REJECTED")
    private String action;

    @Size(max = 1000, message = "Review Comment cannot exceed 1000 characters")
    private String reviewComments;
}