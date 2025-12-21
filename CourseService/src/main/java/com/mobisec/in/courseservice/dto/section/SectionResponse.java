package com.mobisec.in.courseservice.dto.section;


import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionResponse {

    private UUID id;
    private UUID courseId;
    private String title;
    private String description;
    private String objective;
    private Integer orderIndex;
    private Integer totalDurationSeconds;
    private Integer totalLectures;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}