package com.mobisec.in.courseservice.dto.lecture;


import com.mobisec.in.courseservice.enums.LectureContentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLectureRequest {

    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @Size(max = 500, message = "Video URL cannot exceed 500 characters")
    private String videoUrl;

    @Min(value = 0, message = "Duration cannot be negative")
    private Integer durationSeconds;

    private Integer orderIndex;

    private Boolean isPreview;

    private LectureContentType contentType;

    private String articleContent;

    // RESOURCE type fields
    private List<String> resourceUrls;

    private Boolean isCompletedByInstructor;
}