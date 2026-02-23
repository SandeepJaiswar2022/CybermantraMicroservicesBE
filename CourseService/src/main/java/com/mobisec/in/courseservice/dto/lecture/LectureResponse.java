package com.mobisec.in.courseservice.dto.lecture;


import com.mobisec.in.courseservice.enums.LectureContentType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LectureResponse {

    private UUID id;
    private UUID sectionId;
    private String title;
    private String description;
    private String videoUrl;
    private Integer durationSeconds;
    private Integer orderIndex;
    private Boolean isPreview;
    private LectureContentType contentType;
    private String articleContent;
    private List<String> resourceUrls;
    private Boolean isCompletedByInstructor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
