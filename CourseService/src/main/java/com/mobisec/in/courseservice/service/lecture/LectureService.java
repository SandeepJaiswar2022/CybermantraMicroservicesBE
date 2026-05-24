package com.mobisec.in.courseservice.service.lecture;


import com.mobisec.in.courseservice.dto.lecture.*;
import com.mobisec.in.courseservice.dto.section.SectionWithLecturesResponse;

import java.util.List;
import java.util.UUID;

public interface LectureService {

    /**
     * Create a new lecture for a section
     */
    LectureResponse createLecture(
            UUID courseId,
            UUID sectionId,
            CreateLectureRequest request,
            String userRole,
            UUID userId
    );

    /**
     * Get all lectures for a section
     */
    SectionWithLecturesResponse getAllLecturesBySection(
            UUID courseId,
            UUID sectionId,
            UUID userId,
            String userRole,
            boolean isEnrolled
    );

    /**
     * Get lecture by ID
     */
    LectureResponse getLectureById(
            UUID courseId,
            UUID sectionId,
            UUID lectureId,
            UUID userId,
            String userRole,
            boolean isEnrolled
    );

    /**
     * Update lecture details
     */
    LectureResponse updateLecture(
            UUID courseId,
            UUID sectionId,
            UUID lectureId,
            UpdateLectureRequest request,
            String userRole,
            UUID userId
    );

    /**
     * Delete a lecture
     */
    void deleteLecture(
            UUID courseId,
            UUID sectionId,
            UUID lectureId,
            String userRole,
            UUID userId
    );

    /**
     * Reorder lectures within a section
     */
    List<LectureResponse> reorderLectures(
            UUID courseId,
            UUID sectionId,
            ReorderLecturesRequest request,
            String userRole,
            UUID userId
    );

    /**
     * Recalculate section duration (called when lectures are modified)
     */
    void recalculateSectionDuration(UUID sectionId);
}