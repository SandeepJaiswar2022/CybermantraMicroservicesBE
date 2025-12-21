package com.mobisec.in.courseservice.dto.course;


import com.mobisec.in.courseservice.enums.CourseLevel;
import com.mobisec.in.courseservice.enums.CourseStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseSummaryResponse {

    private UUID id;
    private String title;
    private String subtitle;
    private UUID instructorId;
    private String instructorName;
    private String categoryName;
    private CourseLevel level;
    private BigDecimal price;
    private String thumbnailUrl;
    private CourseStatus status;
    private Boolean isPublished;
    private Integer totalDurationSeconds;
    private Integer totalLectures;
    private BigDecimal averageRating;
    private Integer totalRatings;
    private Integer totalEnrollments;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
