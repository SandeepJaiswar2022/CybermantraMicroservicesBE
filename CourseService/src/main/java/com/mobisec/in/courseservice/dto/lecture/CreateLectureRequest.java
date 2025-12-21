package com.mobisec.in.courseservice.dto.lecture;


import com.mobisec.in.courseservice.enums.LectureContentType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLectureRequest {

    @NotNull(message = "Section ID is required")
    private UUID sectionId;

    @NotBlank(message = "Lecture title is required")
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @Size(max = 500, message = "Video URL cannot exceed 500 characters")
    private String videoUrl;

    @Min(value = 0, message = "Duration cannot be negative")
    private Integer durationSeconds;

    @NotNull(message = "Order index is required")
    private Integer orderIndex;

    private Boolean isPreview;

    @NotNull(message = "Content type is required")
    private LectureContentType contentType;

    private String articleContent; // For article type lectures

    private String resourceUrls; // JSON array of resource URLs
}
