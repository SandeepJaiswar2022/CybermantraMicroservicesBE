package com.mobisec.in.courseservice.dto.course;


import com.mobisec.in.courseservice.enums.CourseLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCourseRequest {

    @Size(min = 10, max = 255, message = "Title must be between 10 and 255 characters")
    private String title;

    @Size(max = 500, message = "Subtitle cannot exceed 500 characters")
    private String subtitle;

    @Size(min = 50, message = "Description must be at least 50 characters")
    private String description;

    private UUID categoryId;

    private UUID subcategoryId;

    private CourseLevel level;

    @Size(min = 2, max = 10, message = "Language code must be between 2 and 10 characters")
    private String language;

    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    @DecimalMax(value = "99999.99", message = "Price cannot exceed 99999.99")
    private BigDecimal price;

    @Size(max = 1000, message = "Target audience cannot exceed 1000 characters")
    private String targetAudience;

    @Size(max = 1000, message = "Requirements cannot exceed 1000 characters")
    private String requirements;

    @Size(max = 500, message = "Thumbnail URL cannot exceed 500 characters")
    private String thumbnailUrl;

    @Size(max = 500, message = "Promo video URL cannot exceed 500 characters")
    private String promoVideoUrl;
}