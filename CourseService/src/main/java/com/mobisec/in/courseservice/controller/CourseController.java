package com.mobisec.in.courseservice.controller;


import com.mobisec.in.courseservice.dto.common.ApiResponse;
import com.mobisec.in.courseservice.dto.course.*;
import com.mobisec.in.courseservice.enums.CourseLevel;
import com.mobisec.in.courseservice.enums.CourseStatus;
import com.mobisec.in.courseservice.service.course.CourseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Slf4j
public class CourseController {

    private final CourseService courseService;
    /**
     * CREATE COURSE - Only INSTRUCTOR
     */
    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            HttpServletRequest httpRequest) {

        UUID instructorId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("POST /api/v1/courses - Creating course by instructor: {}", instructorId);

        CourseResponse response = courseService.createCourse(request, instructorId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created successfully", response));
    }

    /**
     * UPDATE COURSE - Only course owner or ADMIN
     */
    @PutMapping("/{courseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("PUT /api/v1/courses/{} - Updating course by user: {}", courseId, userId);

        CourseResponse response = courseService.updateCourse(courseId, request, userId, userRole);

        return ResponseEntity.ok(ApiResponse.success("Course updated successfully", response));
    }

    /**
     * GET COURSE BY ID
     * - Public for published courses
     * - Authenticated for draft courses (owner/admin only)
     */
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> getCourseById(
            @PathVariable UUID courseId,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("GET /api/v1/courses/{} - Fetching course", courseId);

        CourseDetailResponse response = courseService.getCourseById(courseId, userId, userRole);

        return ResponseEntity.ok(ApiResponse.success("Course retrieved successfully", response));
    }

    /**
     * LIST COURSES with Filtering & Pagination
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> listCourses(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID subcategoryId,
            @RequestParam(required = false) CourseLevel level,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("GET /api/v1/courses - Listing courses");

        Pageable pageable = PageRequest.of(page, size, Sort.by(
                sort[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                sort[0]
        ));

        Page<CourseSummaryResponse> courses = courseService.listCourses(
                categoryId, subcategoryId, level, instructorId, status,
                isPublished, search, pageable, userId, userRole
        );

        return ResponseEntity.ok(ApiResponse.success("Courses retrieved successfully", courses));
    }

    /**
     * SUBMIT COURSE FOR REVIEW - Only course owner (INSTRUCTOR)
     */
    @PostMapping("/{courseId}/submit-for-review")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseResponse>> submitForReview(
            @PathVariable UUID courseId,
            HttpServletRequest httpRequest) {

        UUID instructorId = (UUID) httpRequest.getAttribute("userId");

        log.info("POST /api/v1/courses/{}/submit-for-review - By instructor: {}",
                courseId, instructorId);

        CourseResponse response = courseService.submitForReview(courseId, instructorId);

        return ResponseEntity.ok(ApiResponse.success("Course submitted for review", response));
    }

    /**
     * REVIEW COURSE (Approve/Reject) - Only ADMIN
     */
    @PostMapping("/{courseId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> reviewCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody ReviewCourseRequest request,
            HttpServletRequest httpRequest) {

        UUID adminId = (UUID) httpRequest.getAttribute("userId");

        log.info("POST /api/v1/courses/{}/review - By admin: {} with action: {}",
                courseId, adminId, request.getAction());

        CourseResponse response = courseService.reviewCourse(courseId, request);

        return ResponseEntity.ok(ApiResponse.success("Course reviewed successfully", response));
    }

    /**
     * PUBLISH COURSE - Only course owner (INSTRUCTOR) after approval
     */
    @PostMapping("/{courseId}/publish")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseResponse>> publishCourse(
            @PathVariable UUID courseId,
            HttpServletRequest httpRequest) {

        UUID instructorId = (UUID) httpRequest.getAttribute("userId");

        log.info("POST /api/v1/courses/{}/publish - By instructor: {}", courseId, instructorId);

        CourseResponse response = courseService.publishCourse(courseId, instructorId);

        return ResponseEntity.ok(ApiResponse.success("Course published successfully", response));
    }

    /**
     * UNPUBLISH COURSE - Only course owner or ADMIN
     */
    @PostMapping("/{courseId}/unpublish")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> unpublishCourse(
            @PathVariable UUID courseId,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("POST /api/v1/courses/{}/unpublish - By user: {}", courseId, userId);

        CourseResponse response = courseService.unpublishCourse(courseId, userId, userRole);

        return ResponseEntity.ok(ApiResponse.success("Course unpublished successfully", response));
    }

    /**
     * DELETE COURSE (Soft Delete) - Only course owner or ADMIN
     */
    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable UUID courseId,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("DELETE /api/v1/courses/{} - By user: {}", courseId, userId);

        courseService.deleteCourse(courseId, userId, userRole);

        return ResponseEntity.ok(ApiResponse.success("Course deleted successfully", null));
    }
}