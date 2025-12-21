package com.mobisec.in.courseservice.dto.section;


import com.mobisec.in.courseservice.dto.lecture.LectureResponse;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionWithLecturesResponse {

    private UUID id;
    private UUID courseId;
    private String title;
    private String description;
    private String objective;
    private Integer orderIndex;
    private Integer totalDurationSeconds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<LectureResponse> lectures = new ArrayList<>();
}