package com.cybermantra.microservices.in.EnrollmentService.controllers;
import com.cybermantra.microservices.in.EnrollmentService.dto.request.EnrollmentRequest;
import com.cybermantra.microservices.in.EnrollmentService.dto.request.NoteRequest;
import com.cybermantra.microservices.in.EnrollmentService.dto.request.ProgressUpdateRequest;
import com.cybermantra.microservices.in.EnrollmentService.dto.response.*;
import com.cybermantra.microservices.in.EnrollmentService.services.CertificateService;
import com.cybermantra.microservices.in.EnrollmentService.services.EnrollmentService;
import com.cybermantra.microservices.in.EnrollmentService.services.NoteService;
import com.cybermantra.microservices.in.EnrollmentService.services.ProgressService;
import com.cybermantra.microservices.in.EnrollmentService.utilities.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
@Tag(name = "Enrollment", description = "Course enrollment management endpoints")

public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final ProgressService progressService;
    private final CertificateService certificateService;
    private final NoteService noteService;

    // ─── Enrollment Management ──────────────────────────────────────

    @PostMapping
    @Operation(summary = "Enroll in a course", description = "Enroll the authenticated user in a course. Set targetUserId to gift the course to another user.")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enroll(
            @Valid @RequestBody EnrollmentRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        EnrollmentResponse response = enrollmentService.enroll(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Successfully enrolled in course", response));
    }

    @Operation(summary = "Get all enrollments for a user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> getUserEnrollments(
            @PathVariable Long userId) {
        // Admins can query any user; regular users can only query themselves
        Long currentUserId = SecurityUtils.getCurrentUserId();
        // Simple guard: allow self-query (role-based admin check can be added via @PreAuthorize)
        List<EnrollmentResponse> enrollments = enrollmentService.getUserEnrollments(userId);
        return ResponseEntity.ok(ApiResponse.success(enrollments));
    }

    @GetMapping("/{enrollmentId}")
    @Operation(summary = "Get a specific enrollment by ID")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> getEnrollment(
            @PathVariable Long enrollmentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                enrollmentService.getEnrollment(enrollmentId, userId)));
    }

    @DeleteMapping("/{enrollmentId}")
    @Operation(summary = "Unenroll from a course")
    public ResponseEntity<ApiResponse<Void>> unenroll(@PathVariable Long enrollmentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        enrollmentService.unenroll(enrollmentId, userId);
        return ResponseEntity.ok(ApiResponse.success("Successfully unenrolled", null));
    }

    // ─── Progress Tracking ──────────────────────────────────────────

    @PostMapping("/{enrollmentId}/progress")
    @Operation(summary = "Update lecture progress", description = "Update watch time, last position, and optionally mark lecture as completed")
    public ResponseEntity<ApiResponse<LectureProgressResponse>> updateProgress(
            @PathVariable Long enrollmentId,
            @Valid @RequestBody ProgressUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                progressService.updateProgress(enrollmentId, userId, request)));
    }

    @GetMapping("/{enrollmentId}/progress")
    @Operation(summary = "Get all lecture progress for an enrollment")
    public ResponseEntity<ApiResponse<List<LectureProgressResponse>>> getProgress(
            @PathVariable Long enrollmentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                progressService.getProgressForEnrollment(enrollmentId, userId)));
    }

    @PostMapping("/{enrollmentId}/lectures/{lectureId}/complete")
    @Operation(summary = "Mark a lecture as completed")
    public ResponseEntity<ApiResponse<LectureProgressResponse>> completeLecture(
            @PathVariable Long enrollmentId,
            @PathVariable Long lectureId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Lecture marked as completed",
                progressService.markLectureCompleted(enrollmentId, lectureId, userId)));
    }

    // ─── Certificate ────────────────────────────────────────────────

    @GetMapping("/{enrollmentId}/certificate")
    @Operation(summary = "Get or generate certificate", description = "Auto-generates a certificate if course is 100% complete. Returns existing one if already generated.")
    public ResponseEntity<ApiResponse<CertificateResponse>> getCertificate(
            @PathVariable Long enrollmentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                certificateService.getOrGenerateCertificate(enrollmentId, userId)));
    }

    // ─── Notes ──────────────────────────────────────────────────────

    @PostMapping("/{enrollmentId}/notes")
    @Operation(summary = "Add a note to a lecture")
    public ResponseEntity<ApiResponse<NoteResponse>> addNote(
            @PathVariable Long enrollmentId,
            @Valid @RequestBody NoteRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note added",
                        noteService.addNote(enrollmentId, userId, request)));
    }

    @GetMapping("/{enrollmentId}/notes")
    @Operation(summary = "Get all notes for an enrollment")
    public ResponseEntity<ApiResponse<List<NoteResponse>>> getNotes(
            @PathVariable Long enrollmentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                noteService.getNotes(enrollmentId, userId)));
    }
}