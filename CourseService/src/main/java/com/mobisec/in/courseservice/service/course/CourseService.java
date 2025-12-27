package com.mobisec.in.courseservice.service.course;


import com.mobisec.in.courseservice.dto.course.*;
import com.mobisec.in.courseservice.enums.CourseLevel;
import com.mobisec.in.courseservice.enums.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for Course operations
 * Handles course lifecycle: creation, review, approval, publishing, and management
 */
public interface CourseService {

    /**
     * Create a new course with DRAFT status
     * Only accessible by INSTRUCTOR role
     *
     * @param request Course creation details
     * @param instructorId ID of the instructor creating the course
     * @return Created course details
//     * @throws ResourceNotFoundException if category or subcategory not found
//     * @throws InvalidInputException if subcategory doesn't belong to selected category
     */
    CourseResponse createCourse(CreateCourseRequest request, UUID instructorId);

    /**
     * Update an existing course
     * Only course owner (INSTRUCTOR) or ADMIN can update
     * Cannot update courses with UNDER_REVIEW status
     *
     * @param courseId ID of the course to update
     * @param request Updated course details
     * @param userId ID of the user performing the update
     * @param userRole Role of the user (INSTRUCTOR or ADMIN)
     * @return Updated course details
//     * @throws ResourceNotFoundException if course not found
//     * @throws ForbiddenException if user doesn't have permission
//     * @throws InvalidInputException if course is under review
     */
    CourseResponse updateCourse(UUID courseId, UpdateCourseRequest request,
                                UUID userId, String userRole);

    /**
     * Get course by ID with access control
     * - Published courses: accessible by everyone
     * - Draft/Under Review courses: accessible only by owner and ADMIN
     *
     * @param courseId ID of the course
     * @param userId ID of the requesting user (null for public access)
     * @param userRole Role of the requesting user
     * @return Detailed course information including sections
//     * @throws ResourceNotFoundException if course not found or deleted
//     * @throws UnauthorizedException if authentication required for draft course
//     * @throws ForbiddenException if user doesn't have permission to view
     */
    CourseDetailResponse getCourseById(UUID courseId, UUID userId, String userRole);

    /**
     * List courses with pagination and filtering
     * Access control:
     * - Public users: only published courses
     * - Instructors: their own courses + published courses
     * - ADMIN: all courses
     *
     * @param categoryId Filter by category (optional)
     * @param subcategoryId Filter by subcategory (optional)
     * @param level Filter by course level (optional)
     * @param instructorId Filter by instructor (optional)
     * @param status Filter by course status (optional)
     * @param isPublished Filter by publication status (optional)
     * @param searchTerm Search in title and description (optional)
     * @param pageable Pagination parameters
     * @param userId ID of the requesting user (null for public)
     * @param userRole Role of the requesting user
     * @return Page of course summaries
     */
    Page<CourseSummaryResponse> listCourses(
            UUID categoryId,
            UUID subcategoryId,
            CourseLevel level,
            UUID instructorId,
            CourseStatus status,
            Boolean isPublished,
            String searchTerm,
            Pageable pageable,
            UUID userId,
            String userRole);

    /**
     * Submit course for admin review
     * Changes status from DRAFT to UNDER_REVIEW
     * Only course owner can submit
     *
     * @param courseId ID of the course to submit
     * @param instructorId ID of the instructor submitting
     * @return Updated course details
//     * @throws ResourceNotFoundException if course not found
//     * @throws ForbiddenException if user is not the course owner
//     * @throws InvalidInputException if course is not in DRAFT status
     */
    CourseResponse submitForReview(UUID courseId, UUID instructorId);

    /**
     * Review course - approve or reject
     * Only ADMIN role can review courses
     * Changes status to APPROVED or REJECTED
     *
     * @param courseId ID of the course to review
     * @param request Review action (APPROVE/REJECT) and optional comments
     * @return Updated course details
//     * @throws ResourceNotFoundException if course not found
//     * @throws InvalidInputException if course is not UNDER_REVIEW or invalid action
     */
    CourseResponse reviewCourse(UUID courseId, ReviewCourseRequest request);

    /**
     * Publish an approved course
     * Makes course visible to public
     * Only course owner can publish
     * Course must be in APPROVED status
     *
     * @param courseId ID of the course to publish
     * @param instructorId ID of the instructor publishing
     * @return Published course details
//     * @throws ResourceNotFoundException if course not found
//     * @throws ForbiddenException if user is not the course owner
//     * @throws InvalidInputException if course is not APPROVED
     */
    CourseResponse publishCourse(UUID courseId, UUID instructorId);

    /**
     * Unpublish a published course
     * Hides course from public, changes status back to DRAFT
     * Only course owner or ADMIN can unpublish
     *
     * @param courseId ID of the course to unpublish
     * @param userId ID of the user performing action
     * @param userRole Role of the user (INSTRUCTOR or ADMIN)
     * @return Unpublished course details
//     * @throws ResourceNotFoundException if course not found
//     * @throws ForbiddenException if user doesn't have permission
//     * @throws InvalidInputException if course is already unpublished
     */
    CourseResponse unpublishCourse(UUID courseId, UUID userId, String userRole);

    /**
     * Soft delete a course
     * Marks course as deleted without removing from database
     * Also unpublishes the course if it was published
     * Only course owner or ADMIN can delete
     *
     * @param courseId ID of the course to delete
     * @param userId ID of the user performing deletion
     * @param userRole Role of the user (INSTRUCTOR or ADMIN)
//     * @throws ResourceNotFoundException if course not found
//     * @throws ForbiddenException if user doesn't have permission
     */
    void deleteCourse(UUID courseId, UUID userId, String userRole);

    /**
     * Get all courses by instructor
     * Used for instructor dashboard
     *
     * @param instructorId ID of the instructor
     * @param pageable Pagination parameters
     * @return Page of instructor's courses
     */
    Page<CourseSummaryResponse> getCoursesByInstructor(UUID instructorId, Pageable pageable);

    /**
     * Get courses pending review
     * Only ADMIN can access this
     *
     * @param pageable Pagination parameters
     * @return Page of courses with UNDER_REVIEW status
     */
    Page<CourseSummaryResponse> getCoursesUnderReview(Pageable pageable);

    /**
     * Get course statistics for instructor
     * Includes total courses, published, drafts, enrollments, etc.
     *
     * @param instructorId ID of the instructor
     * @return Course statistics
     */
//    CourseStatisticsResponse getInstructorStatistics(UUID instructorId);

    /**
     * Restore a soft-deleted course
     * Only ADMIN can restore courses
     *
     * @param courseId ID of the course to restore
     * @return Restored course details
//     * @throws ResourceNotFoundException if course not found
     */
    CourseResponse restoreCourse(UUID courseId);

    /**
     * Validate if course can be published
     * Checks if course has minimum required content
     *
     * @param courseId ID of the course to validate
     * @return Validation result with list of issues
     */
//    CourseValidationResponse validateCourseForPublishing(UUID courseId);
}
