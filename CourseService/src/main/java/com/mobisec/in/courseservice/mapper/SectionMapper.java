package com.mobisec.in.courseservice.mapper;


import com.mobisec.in.courseservice.dto.section.SectionResponse;
import com.mobisec.in.courseservice.entity.CourseSection;
import org.springframework.stereotype.Component;



@Component
public class SectionMapper {

    /**
     * Convert CourseSection entity to SectionResponse DTO
     */
    public SectionResponse toResponse(CourseSection section) {
        if (section == null) {
            return null;
        }

        return SectionResponse.builder()
                .id(section.getId())
                .courseId(section.getCourse().getId())
                .title(section.getTitle())
                .description(section.getDescription())
                .objective(section.getObjective())
                .orderIndex(section.getOrderIndex())
                .totalDurationSeconds(section.getTotalDurationSeconds())
                .totalLectures(section.getLectures() != null ? section.getLectures().size() : 0)
                .createdAt(section.getCreatedAt())
                .updatedAt(section.getUpdatedAt())
                .build();
    }



}