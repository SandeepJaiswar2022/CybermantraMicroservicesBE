package com.mobisec.in.courseservice.dto.course;



import com.mobisec.in.courseservice.dto.section.SectionWithLecturesResponse;
import com.mobisec.in.courseservice.enums.CourseLevel;
import com.mobisec.in.courseservice.enums.CourseStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseDetailResponse {

    private UUID id;
    private UUID instructorId;
    private String instructorName;
    private String instructorBio;
    private String title;
    private String subtitle;
    private String description;
    private UUID categoryId;
    private String categoryName;
    private UUID subcategoryId;
    private String subcategoryName;
    private CourseLevel level;
    private String language;
    private BigDecimal price;
    private String thumbnailUrl;
    private String promoVideoUrl;
    private CourseStatus status;
    private Boolean isPublished;
    private String targetAudience;
    private String requirements;
    private Integer totalDurationSeconds;
    private Integer totalLectures;
    private Integer totalSections;
    private BigDecimal averageRating;
    private Integer totalRatings;
    private Integer totalEnrollments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    @Builder.Default
    private List<SectionWithLecturesResponse> sections = new ArrayList<>();
}
