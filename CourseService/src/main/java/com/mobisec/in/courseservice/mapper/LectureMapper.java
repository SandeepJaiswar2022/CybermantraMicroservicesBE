package com.mobisec.in.courseservice.mapper;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobisec.in.courseservice.dto.lecture.LectureResponse;
import com.mobisec.in.courseservice.dto.section.SectionWithLecturesResponse;
import com.mobisec.in.courseservice.entity.CourseSection;
import com.mobisec.in.courseservice.entity.Lecture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class LectureMapper {

    private final ObjectMapper objectMapper;

    /**
     * Convert Lecture entity to LectureResponse DTO
     */
    public LectureResponse toResponse(Lecture lecture) {
        if (lecture == null) {
            return null;
        }

        return LectureResponse.builder()
                .id(lecture.getId())
                .sectionId(lecture.getSection().getId())
                .title(lecture.getTitle())
                .description(lecture.getDescription())
                .contentType(lecture.getContentType())
                .videoUrl(lecture.getVideoUrl())
                .durationSeconds(lecture.getDurationSeconds())
                .articleContent(lecture.getArticleContent())
                .resourceUrls(deserializeResourceUrls(lecture.getResourceUrls()))
                .orderIndex(lecture.getOrderIndex())
                .isPreview(lecture.getIsPreview())
                .isCompletedByInstructor(lecture.getIsCompletedByInstructor())
                .createdAt(lecture.getCreatedAt())
                .updatedAt(lecture.getUpdatedAt())
                .build();
    }

    /**
     * Convert list of Lecture entities to list of LectureResponse DTOs
     */
    public List<LectureResponse> toResponseList(List<Lecture> lectures) {
        if (lectures == null || lectures.isEmpty()) {
            return Collections.emptyList();
        }

        return lectures.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Convert CourseSection entity to SectionWithLecturesResponse DTO
     */
    public SectionWithLecturesResponse toResponseWithLectures(CourseSection section) {
        if (section == null) {
            return null;
        }

        List<LectureResponse> lectures = section.getLectures() != null
                ? section.getLectures().stream()
                .map(this::toResponse)
                .collect(Collectors.toList())
                : List.of();

        return SectionWithLecturesResponse.builder()
                .courseId(section.getCourse().getId())
                .sectionId(section.getId())
                .title(section.getTitle())
                .description(section.getDescription())
                .objective(section.getObjective())
                .orderIndex(section.getOrderIndex())
                .totalDurationSeconds(section.getTotalDurationSeconds())
                .totalLectures(lectures.size())
                .lectures(lectures)
                .createdAt(section.getCreatedAt())
                .updatedAt(section.getUpdatedAt())
                .build();
    }

    /**
     * Serialize list of resource URLs to JSON string
     * Used when saving to database
     */
    public String serializeResourceUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(urls);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize resource URLs: {}", urls, e);
            throw new RuntimeException("Failed to serialize resource URLs", e);
        }
    }

    /**
     * Deserialize JSON string to list of resource URLs
     * Used when reading from database
     */
    public List<String> deserializeResourceUrls(String json) {
        if (json == null || json.isEmpty() || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize resource URLs from JSON: {}", json, e);
            return Collections.emptyList();
        }
    }
}