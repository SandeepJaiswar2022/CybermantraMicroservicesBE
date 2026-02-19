package com.mobisec.in.courseservice.controller;


import com.mobisec.in.courseservice.dto.common.ApiResponse;
import com.mobisec.in.courseservice.dto.section.*;
import com.mobisec.in.courseservice.service.section.SectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/sections")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Course Sections", description = "Section management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class SectionController {

    private final SectionService sectionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Create a new section", description = "Creates a new section for the specified course. Only course owner can create sections.")
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateSectionRequest request,
            HttpServletRequest httpRequest) {


        log.info("REST request to create section for course: {}", courseId);
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        SectionResponse response = sectionService.createSection(courseId, request, userRole,userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.<SectionResponse>builder()
                        .success(true)
                        .message("Section created successfully")
                        .data(response)
                        .build());
    }

    @GetMapping
    @Operation(summary = "Get all sections", description = "Retrieves all sections for a course ordered by position")
    public ResponseEntity<ApiResponse<List<SectionResponse>>> getAllSections(
            @PathVariable UUID courseId) {

        log.debug("REST request to get all sections for course: {}", courseId);

        List<SectionResponse> sections = sectionService.getAllSectionsByCourse(courseId);

        return ResponseEntity.ok(ApiResponse.<List<SectionResponse>>builder()
                .success(true)
                .message("Sections retrieved successfully")
                .data(sections)
                .build());
    }

    @GetMapping("/{sectionId}")
    @Operation(summary = "Get section by ID", description = "Retrieves a specific section by ID")
    public ResponseEntity<ApiResponse<SectionResponse>> getSectionById(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId) {

        log.debug("REST request to get section: {} for course: {}", sectionId, courseId);

        SectionResponse section = sectionService.getSectionById(courseId, sectionId);

        return ResponseEntity.ok(ApiResponse.<SectionResponse>builder()
                .success(true)
                .message("Section retrieved successfully")
                .data(section)
                .build());
    }



    @PutMapping("/{sectionId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Update section", description = "Updates section details. Only course owner can update sections.")
    public ResponseEntity<ApiResponse<SectionResponse>> updateSection(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody UpdateSectionRequest request,
            HttpServletRequest httpRequest) {

        log.info("REST request to update section: {} for course: {}", sectionId, courseId);
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        SectionResponse response = sectionService.updateSection(courseId, sectionId, request, userRole,userId);

        return ResponseEntity.ok(ApiResponse.<SectionResponse>builder()
                .success(true)
                .message("Section updated successfully")
                .data(response)
                .build());
    }

    @DeleteMapping("/{sectionId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Delete section", description = "Deletes a section and all its lectures. Only course owner can delete sections.")
    public ResponseEntity<ApiResponse<Void>> deleteSection(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            HttpServletRequest httpRequest) {

        log.info("REST request to delete section: {} from course: {}", sectionId, courseId);
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        sectionService.deleteSection(courseId, sectionId,userRole,userId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Section deleted successfully")
                .build());
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Reorder sections", description = "Changes the order of sections in a course. Only course owner can reorder sections.")
    public ResponseEntity<ApiResponse<List<SectionResponse>>> reorderSections(
            @PathVariable UUID courseId,
            @Valid @RequestBody ReorderSectionsRequest request,
            HttpServletRequest httpRequest) {

        log.info("REST request to reorder sections for course: {}", courseId);
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        List<SectionResponse> sections = sectionService.reorderSections(courseId, request, userRole,userId);

        return ResponseEntity.ok(ApiResponse.<List<SectionResponse>>builder()
                .success(true)
                .message("Sections reordered successfully")
                .data(sections)
                .build());
    }
}