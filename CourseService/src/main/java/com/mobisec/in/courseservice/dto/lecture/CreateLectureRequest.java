package com.mobisec.in.courseservice.dto.lecture;


import com.mobisec.in.courseservice.enums.LectureContentType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLectureRequest {

    @NotBlank(message = "Lecture title is required")
    @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @NotNull(message = "Content type is required")
    private LectureContentType contentType;

    // VIDEO type fields
    @Size(max = 500, message = "Video URL cannot exceed 500 characters")
    private String videoUrl;

    @Min(value = 0, message = "Duration cannot be negative")
    private Integer durationSeconds;

    // ARTICLE type fields
    @Size(max = 50000, message = "Article content cannot exceed 50000 characters")
    private String articleContent;

    // RESOURCE type fields
    private List<String> resourceUrls;

    @Builder.Default
    private Boolean isPreview = false;

    // Note: orderIndex is auto-calculated, not provided in request
}