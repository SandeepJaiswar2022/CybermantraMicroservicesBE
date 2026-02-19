package com.mobisec.in.courseservice.service.course;


import com.mobisec.in.courseservice.dto.course.*;
import com.mobisec.in.courseservice.entity.Category;
import com.mobisec.in.courseservice.entity.Course;
import com.mobisec.in.courseservice.enums.CourseLevel;
import com.mobisec.in.courseservice.enums.CourseStatus;
import com.mobisec.in.courseservice.exception.*;
import com.mobisec.in.courseservice.repository.CategoryRepository;
import com.mobisec.in.courseservice.repository.CourseRepository;
import com.mobisec.in.courseservice.repository.specification.CourseSpecifications;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;

    /**
     * CREATE COURSE - Only INSTRUCTOR and ADMIN
     * Initial status: DRAFT
     */
    @Override
    public CourseResponse createCourse(CreateCourseRequest request,String userRole, UUID loggedInUserId) {
        log.info("Creating course: {} by userId: {} having role: {}", request.getTitle(), loggedInUserId, userRole);

        UUID instructorId;

        if ("ADMIN".equals(userRole)) {

            if (request.getInstructorId() == null) {
                throw new InvalidInputException("Instructor ID is required when admin creates a course");
            }
/* *****IMP***** */
            //Call User Profile service to verify if Instructor exist or not.
//            userRepository.findById(instructorId)
//                    .orElseThrow(() -> new ResourceNotFoundException("Instructor not found"));
//
            //Call User Profile service to verify if Instructor exist or not if exist then check for role.
            instructorId = request.getInstructorId();

        } else if ("INSTRUCTOR".equals(userRole)) {

            instructorId = loggedInUserId;

        } else {
            throw new ForbiddenException("You are not allowed to create courses");
        }

        // Validate category exists
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        
        // Validate subcategory if provided
        Category subcategory = null;
        if (request.getSubcategoryId() != null) {
            subcategory = categoryRepository.findById(request.getSubcategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found"));

            // Validate subcategory belongs to selected category
            if (!subcategory.getParent().getId().equals(category.getId())) {
                throw new InvalidInputException("Subcategory does not belong to selected category");
            }
        }

        boolean alreadyExists = courseRepository
                .existsByInstructorIdAndTitleIgnoreCaseAndCategory_Id(
                        instructorId,
                        request.getTitle(),
                        request.getCategoryId()
                );

        if (alreadyExists) {
            throw new DuplicateResourceException("Course with the same title already exists for this instructor");
        }


        // Build course entity
        Course course = Course.builder()
                .instructorId(instructorId)
                .title(request.getTitle())
                .subtitle(request.getSubtitle())
                .description(request.getDescription())
                .category(category)
                .subcategory(subcategory)
                .level(request.getLevel())
                .language(request.getLanguage())
                .price(request.getPrice())
                .thumbnailUrl(request.getThumbnailUrl())
                .promoVideoUrl(request.getPromoVideoUrl())
                .targetAudience(request.getTargetAudience())
                .requirements(request.getRequirements())
                .status(CourseStatus.DRAFT) // Always start as DRAFT
                .isPublished(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        Course savedCourse = courseRepository.save(course);
        log.info("Course created successfully with id: {}", savedCourse.getId());

        return mapToResponse(savedCourse);
    }

    /**
     * UPDATE COURSE - Only course owner (INSTRUCTOR) or ADMIN
     */
    @Override
    public CourseResponse updateCourse(UUID courseId, UpdateCourseRequest request,
                                       UUID userId, String userRole) {
        log.info("Updating course: {} by user: {}", courseId, userId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        // Authorization check
        validateCourseOwnership(course, userId, userRole);

        // Can't update if course is UNDER_REVIEW or PUBLISHED
        if (course.getStatus() == CourseStatus.PENDING_REVIEW) {
            throw new InvalidInputException("Cannot update course while under review");
        }

        // Update fields
        if (request.getTitle() != null) course.setTitle(request.getTitle());
        if (request.getSubtitle() != null) course.setSubtitle(request.getSubtitle());
        if (request.getDescription() != null) course.setDescription(request.getDescription());
        if (request.getLevel() != null) course.setLevel(request.getLevel());
        if (request.getLanguage() != null) course.setLanguage(request.getLanguage());
        if (request.getPrice() != null) course.setPrice(request.getPrice());
        if (request.getThumbnailUrl() != null) course.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getPromoVideoUrl() != null) course.setPromoVideoUrl(request.getPromoVideoUrl());
        if (request.getTargetAudience() != null) course.setTargetAudience(request.getTargetAudience());
        if (request.getRequirements() != null) course.setRequirements(request.getRequirements());

        // Update category if changed
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            course.setCategory(category);
        }

        // Update subcategory if changed
        if (request.getSubcategoryId() != null) {
            Category subcategory = categoryRepository.findById(request.getSubcategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found"));
            course.setSubcategory(subcategory);
        }

        Course updatedCourse = courseRepository.save(course);
        log.info("Course updated successfully: {}", courseId);

        return mapToResponse(updatedCourse);
    }

    /**
     * GET COURSE BY ID
     * - Public for PUBLISHED courses
     * - Owner + ADMIN for DRAFT/UNDER_REVIEW courses
     */
    @Override
    public CourseDetailResponse getCourseById(UUID courseId, UUID userId, String userRole) {
        log.info("Fetching course: {} by user: {}", courseId, userId);

        Course course = courseRepository.findByIdAndIsDeletedFalse(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        // Authorization check for non-published courses
        if (!course.getIsPublished()) {
            if (userId == null) {
                throw new UnauthorizedException("Authentication required to view draft courses");
            }
            boolean isOwner = course.getInstructorId().equals(userId);
            boolean isAdmin = "ADMIN".equals(userRole);
            if (!isOwner && !isAdmin) {
                throw new ForbiddenException("You don't have permission to view this course");
            }
        }
        return mapToDetailResponse(course);
    }

    /**
     * LIST COURSES with Pagination & Filtering
     */

        @Override
        public Page<CourseSummaryResponse> listCourses(
                UUID categoryId,
                UUID subcategoryId,
                CourseLevel level,
                UUID instructorId,
                CourseStatus status,
                Boolean isPublished,
                String searchTerm,
                Pageable pageable,
                UUID userId,
                String userRole) {

            log.info("Listing courses | userId={}, role={}, instructorId={}, status={}",
                    userId, userRole, instructorId, status);

            Specification<Course> spec = Specification.where(CourseSpecifications.isNotDeleted());

            /* -------------------------------------------------
             * 🌍 PUBLIC USER (Not Logged In)
             * ------------------------------------------------- */
            if (userId == null) {
                // Public can ONLY see published courses
                spec = spec.and(CourseSpecifications.isPublished());

                // Ignore any non-published status sent by client
                if (status != null && status != CourseStatus.PUBLISHED) {
                    log.warn("Public user attempted to filter by non-published status: {}", status);
                }
            }

            /* -------------------------------------------------
             * 👨‍🏫 INSTRUCTOR
             * ------------------------------------------------- */
            else if ("INSTRUCTOR".equals(userRole)) {

                // If instructorId is provided, it MUST match logged-in user
                if (instructorId != null) {
                    if (!instructorId.equals(userId)) {
                        throw new ForbiddenException(
                                "You are not allowed to view courses of another instructor");
                    }
                    spec = spec.and(CourseSpecifications.byInstructorId(instructorId));
                } else {
                    // instructorId not sent → fetch own courses
                    spec = spec.and(CourseSpecifications.byInstructorId(userId));
                }

                // Instructor can see ALL statuses of their own courses
                if (status != null) {
                    spec = spec.and(CourseSpecifications.byStatus(status));
                }

                // Instructor can filter by published flag (optional)
                if (isPublished != null) {
                    spec = spec.and(CourseSpecifications.byPublishedStatus(isPublished));
                }
            }

            /* -------------------------------------------------
             * 👑 ADMIN
             * ------------------------------------------------- */
            else if ("ADMIN".equals(userRole)) {

                if (instructorId != null) {
                    spec = spec.and(CourseSpecifications.byInstructorId(instructorId));
                }

                if (status != null) {
                    spec = spec.and(CourseSpecifications.byStatus(status));
                }

                if (isPublished != null) {
                    spec = spec.and(CourseSpecifications.byPublishedStatus(isPublished));
                }
            }

            /* -------------------------------------------------
             * ❌ UNKNOWN ROLE
             * ------------------------------------------------- */
//            else {
//                throw new UnauthorizedException("Invalid user role");
//            }

            /* -------------------------------------------------
             * COMMON FILTERS (Applicable to all)
             * ------------------------------------------------- */
            if (categoryId != null) {
                spec = spec.and(CourseSpecifications.byCategoryId(categoryId));
            }

            if (subcategoryId != null) {
                spec = spec.and(CourseSpecifications.bySubcategoryId(subcategoryId));
            }

            if (level != null) {
                spec = spec.and(CourseSpecifications.byLevel(level));
            }

            if (searchTerm != null && !searchTerm.isBlank()) {
                spec = spec.and(CourseSpecifications.searchByTitleOrDescription(searchTerm));
            }

            Page<Course> coursePage = courseRepository.findAll(spec, pageable);
            return coursePage.map(this::mapToSummaryResponse);
        }

    /**
     * SUBMIT COURSE FOR REVIEW - Only course owner
     */
    @Override
    public CourseResponse submitForReview(UUID courseId, UUID userId, String userRole) {
        log.info("Submitting course: {} for review by instructor: {}", courseId, userId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        // Validating ownership and Authorization check
        validateCourseOwnership(course, userId, userRole);

        // Validate course is in DRAFT status
        if (course.getStatus() != CourseStatus.DRAFT) {
            throw new InvalidInputException("Only draft courses can be submitted for review");
        }

        // Validate course has minimum content (optional - add your rules)
        validateCourseCompleteness(course);

        course.setStatus(CourseStatus.PENDING_REVIEW);
        Course updatedCourse = courseRepository.save(course);

        log.info("Course submitted for review: {}", courseId);
        return mapToResponse(updatedCourse);
    }

    /**
     * REVIEW COURSE (Approve/Reject) - Only ADMIN
     */
    @Override
    public CourseResponse reviewCourse(UUID courseId, ReviewCourseRequest request) {
        log.info("Reviewing course: {} with action: {}", courseId, request.getAction());

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        // Validate course is under review
        if (course.getStatus() != CourseStatus.PENDING_REVIEW) {
            throw new InvalidInputException("Only courses under review can be approved/rejected");
        }

        if ("APPROVED".equalsIgnoreCase(request.getAction())) {
            course.setStatus(CourseStatus.APPROVED);
            log.info("Course approved: {}", courseId);
        } else if ("REJECTED".equalsIgnoreCase(request.getAction())) {
            course.setStatus(CourseStatus.REJECTED);
            log.info("Course rejected: {}", courseId);
        } else {
            throw new InvalidInputException("Invalid review action. Use APPROVED or REJECTED");
        }

        Course updatedCourse = courseRepository.save(course);
        return mapToResponse(updatedCourse);
    }

    /**
     * PUBLISH COURSE - Only course owner (after approval)
     */
    @Override
    public CourseResponse publishCourse(UUID courseId, UUID instructorId) {
        log.info("Publishing course: {} by instructor: {}", courseId, instructorId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        // Validate ownership
        if (!course.getInstructorId().equals(instructorId)) {
            throw new ForbiddenException("You can only publish your own courses");
        }

        // Validate course is approved
        if (course.getStatus() != CourseStatus.APPROVED) {
            throw new InvalidInputException("Only approved courses can be published");
        }

        course.setIsPublished(true);
        course.setPublishedAt(LocalDateTime.now());

        Course publishedCourse = courseRepository.save(course);
        log.info("Course published successfully: {}", courseId);

        return mapToResponse(publishedCourse);
    }

    /**
     * UNPUBLISH COURSE - Only course owner or ADMIN
     */
    @Override
    public CourseResponse unpublishCourse(UUID courseId, UUID userId, String userRole) {
        log.info("Unpublishing course: {} by user: {}", courseId, userId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        validateCourseOwnership(course, userId, userRole);

        if (!course.getIsPublished()) {
            throw new InvalidInputException("Course is already unpublished");
        }

        course.setIsPublished(false);
        course.setStatus(CourseStatus.DRAFT);

        Course unpublishedCourse = courseRepository.save(course);
        log.info("Course unpublished successfully: {}", courseId);

        return mapToResponse(unpublishedCourse);
    }

    /**
     * DELETE COURSE (Soft Delete) - Only course owner or ADMIN
     */
    @Override
    public void deleteCourse(UUID courseId, UUID userId, String userRole) {
        log.info("Deleting course: {} by user: {}", courseId, userId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        validateCourseOwnership(course, userId, userRole);

        // Soft delete
        course.setIsDeleted(true);
        course.setDeletedAt(LocalDateTime.now());
        course.setIsPublished(false); // Unpublish if published

        courseRepository.save(course);
        log.info("Course soft deleted successfully: {}", courseId);
    }

    @Override
    public Page<CourseSummaryResponse> getCoursesByInstructor(UUID instructorId, Pageable pageable) {
        return null;
    }

    @Override
    public Page<CourseSummaryResponse> getCoursesUnderReview(Pageable pageable) {
        return null;
    }

    @Override
    public CourseResponse restoreCourse(UUID courseId) {
        return null;
    }

    // ==================== Helper Methods ====================

    private void validateCourseOwnership(Course course, UUID userId, String userRole) {
        boolean isOwner = course.getInstructorId().equals(userId);
        boolean isAdmin = "ADMIN".equals(userRole);

        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("You don't have permission to modify this course");
        }
    }

    private void validateCourseCompleteness(Course course) {
        if (course.getTitle() == null || course.getTitle().isBlank()) {
            throw new InvalidInputException("Course must have a title");
        }
        if (course.getDescription() == null || course.getDescription().isBlank()) {
            throw new InvalidInputException("Course must have a description");
        }
        // Add more validation rules as needed
    }

    private CourseResponse mapToResponse(Course course) {
        return CourseResponse.builder()
                .id(course.getId())
                .instructorId(course.getInstructorId())
                .title(course.getTitle())
                .subtitle(course.getSubtitle())
                .description(course.getDescription())
                .categoryId(course.getCategory().getId())
                .categoryName(course.getCategory().getName())
                .subcategoryId(course.getSubcategory() != null ? course.getSubcategory().getId() : null)
                .subcategoryName(course.getSubcategory() != null ? course.getSubcategory().getName() : null)
                .level(course.getLevel())
                .language(course.getLanguage())
                .price(course.getPrice())
                .thumbnailUrl(course.getThumbnailUrl())
                .promoVideoUrl(course.getPromoVideoUrl())
                .status(course.getStatus())
                .isPublished(course.getIsPublished())
                .targetAudience(course.getTargetAudience())
                .requirements(course.getRequirements())
                .totalDurationSeconds(course.getTotalDurationSeconds())
                .totalLectures(course.getTotalLectures())
                .averageRating(course.getAverageRating())
                .totalRatings(course.getTotalRatings())
                .totalEnrollments(course.getTotalEnrollments())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .publishedAt(course.getPublishedAt())
                .build();
    }

    private CourseDetailResponse mapToDetailResponse(Course course) {
        CourseDetailResponse response = new CourseDetailResponse();

        response.setId(course.getId());
        response.setTitle(course.getTitle());
        response.setSubtitle(course.getSubtitle());
        response.setDescription(course.getDescription());
        response.setInstructorId(course.getInstructorId());

        if (course.getCategory() != null) {
            response.setCategoryId(course.getCategory().getId());
            response.setCategoryName(course.getCategory().getName());
        }

        if (course.getSubcategory() != null) {
            response.setSubcategoryId(course.getSubcategory().getId());
            response.setSubcategoryName(course.getSubcategory().getName());
        }

        response.setLevel(course.getLevel());
        response.setLanguage(course.getLanguage());
        response.setPrice(course.getPrice() != null ? course.getPrice() : BigDecimal.ZERO);
        response.setThumbnailUrl(course.getThumbnailUrl());
        response.setPromoVideoUrl(course.getPromoVideoUrl());
        response.setStatus(course.getStatus());
        response.setIsPublished(Boolean.TRUE.equals(course.getIsPublished()));
        response.setTargetAudience(course.getTargetAudience());
        response.setRequirements(course.getRequirements());

        response.setTotalDurationSeconds(course.getTotalDurationSeconds());
        response.setTotalLectures(course.getTotalLectures());
        response.setTotalSections(course.getTotalSections());

        response.setAverageRating(
                course.getAverageRating() != null ? course.getAverageRating() : BigDecimal.ZERO
        );
        response.setTotalRatings(course.getTotalRatings());
        response.setTotalEnrollments(course.getTotalEnrollments());

        response.setCreatedAt(course.getCreatedAt());
        response.setUpdatedAt(course.getUpdatedAt());
        response.setPublishedAt(course.getPublishedAt());

        return response;
    }


    private CourseSummaryResponse mapToSummaryResponse(Course course) {
        return CourseSummaryResponse.builder()
                .id(course.getId())
                .title(course.getTitle())
                .subtitle(course.getSubtitle())
                .instructorId(course.getInstructorId())
                .instructorName(null)
                .thumbnailUrl(course.getThumbnailUrl())
                .categoryName(course.getCategory().getName())
                .level(course.getLevel())
                .price(course.getPrice())
                .averageRating(course.getAverageRating())
                .totalRatings(course.getTotalRatings())
                .totalEnrollments(course.getTotalEnrollments())
                .totalDurationSeconds(course.getTotalDurationSeconds())
                .totalLectures(course.getTotalLectures())
                .isPublished(course.getIsPublished())
                .build();
    }
}