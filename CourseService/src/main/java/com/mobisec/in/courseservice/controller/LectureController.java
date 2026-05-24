package com.mobisec.in.courseservice.controller;


import com.mobisec.in.courseservice.dto.lecture.CreateLectureRequest;
import com.mobisec.in.courseservice.dto.lecture.LectureResponse;
import com.mobisec.in.courseservice.dto.lecture.ReorderLecturesRequest;
import com.mobisec.in.courseservice.dto.lecture.UpdateLectureRequest;
import com.mobisec.in.courseservice.dto.section.SectionWithLecturesResponse;
import com.mobisec.in.courseservice.service.lecture.LectureService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/sections/{sectionId}/lectures")
@RequiredArgsConstructor
@Slf4j
public class LectureController {

    private final LectureService lectureService;
    // Assume you have EnrollmentService to check if user is enrolled
    // private final EnrollmentService enrollmentService;

    /**
     * CREATE LECTURE
     */
    @PostMapping
    public ResponseEntity<LectureResponse> createLecture(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody CreateLectureRequest request,
            HttpServletRequest httpRequest

    ) {
        log.info("Request to create lecture in section {} for course {}", sectionId, courseId);

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        LectureResponse response = lectureService.createLecture(
                courseId,
                sectionId,
                request,
                userRole,
                userId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * UPDATE LECTURE
     */
    @PutMapping("/{lectureId}")
    public ResponseEntity<LectureResponse> updateLecture(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @PathVariable UUID lectureId,
            @Valid @RequestBody UpdateLectureRequest request,
            HttpServletRequest httpRequest

    ) {
        log.info("Request to update lecture {} in section {}", lectureId, sectionId);

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        LectureResponse response = lectureService.updateLecture(
                courseId,
                sectionId,
                lectureId,
                request,
                userRole,
                userId
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET LECTURE BY ID
     */
    @GetMapping("/{lectureId}")
    public ResponseEntity<LectureResponse> getLectureById(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @PathVariable UUID lectureId,
            HttpServletRequest httpRequest
    ) {
        log.info("Request to fetch lecture {} from section {}", lectureId, sectionId);

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        // TODO: Check enrollment status from EnrollmentService
        boolean isEnrolled = false; // enrollmentService.isUserEnrolled(userId, courseId);

        LectureResponse response = lectureService.getLectureById(
                courseId,
                sectionId,
                lectureId,
                userId,
                userRole,
                isEnrolled
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET ALL LECTURES BY SECTION => Delete from the Section Controller and Implement here
     */
    @GetMapping
    public ResponseEntity<SectionWithLecturesResponse> getAllLecturesBySection(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            HttpServletRequest httpRequest

    ) {
        log.info("Request to fetch all lectures for section {}", sectionId);

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        // TODO: Check enrollment status
        boolean isEnrolled = false; // enrollmentService.isUserEnrolled(userId, courseId);

        SectionWithLecturesResponse response = lectureService.getAllLecturesBySection(
                courseId,
                sectionId,
                userId,
                userRole,
                isEnrolled
        );

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE LECTURE
     */
    @DeleteMapping("/{lectureId}")
    public ResponseEntity<Void> deleteLecture(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @PathVariable UUID lectureId,
            HttpServletRequest httpRequest
    ) {
        log.info("Request to delete lecture {} from section {}", lectureId, sectionId);

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        lectureService.deleteLecture(courseId, sectionId, lectureId, userRole, userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * REORDER LECTURES
     */
    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorderLectures(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody ReorderLecturesRequest request,
            HttpServletRequest httpRequest
    ) {

        log.info("Request to reorder lectures in section {}", sectionId);

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        lectureService.reorderLectures(courseId, sectionId, request, userRole, userId);

        return ResponseEntity.ok().build();
    }
}
