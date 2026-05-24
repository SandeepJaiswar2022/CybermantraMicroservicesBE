package com.mobisec.in.courseservice.service.lecture;

import com.mobisec.in.courseservice.dto.section.SectionWithLecturesResponse;
import com.mobisec.in.courseservice.enums.LectureContentType;
import com.mobisec.in.courseservice.mapper.LectureMapper;
import org.springframework.stereotype.Service;
import com.mobisec.in.courseservice.dto.lecture.*;
import com.mobisec.in.courseservice.entity.Course;
import com.mobisec.in.courseservice.entity.CourseSection;
import com.mobisec.in.courseservice.entity.Lecture;
import com.mobisec.in.courseservice.enums.CourseStatus;
import com.mobisec.in.courseservice.exception.ForbiddenException;
import com.mobisec.in.courseservice.exception.InvalidInputException;
import com.mobisec.in.courseservice.exception.ResourceNotFoundException;
import com.mobisec.in.courseservice.repository.CourseRepository;
import com.mobisec.in.courseservice.repository.LectureRepository;
import com.mobisec.in.courseservice.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LectureServiceImpl implements LectureService {

    private final LectureRepository lectureRepository;
    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;
    private final LectureMapper lectureMapper;

    /**
     * Create a new lecture for a section
     */
    @Override
    @Transactional
    public LectureResponse createLecture(
            UUID courseId,
            UUID sectionId,
            CreateLectureRequest request,
            String userRole,
            UUID userId
    ) {
        log.info("Creating lecture for section: {} in course: {}", sectionId, courseId);

        // Find section and verify it belongs to the course
        CourseSection section = findSectionAndVerifyCourse(sectionId, courseId);

        // Verify ownership (only course owner or admin can create)
        verifyCourseOwnership(section.getCourse(), userId, userRole);

        // Validate lecture content based on content type
        validateLectureContent(request);

        // Check duplicate title inside same section
        if (lectureRepository.existsBySection_IdAndTitleIgnoreCase(sectionId, request.getTitle())) {
            throw new IllegalArgumentException("Lecture with same title already exists in this section");
        }

        // Auto-calculate next order index
        Integer maxOrderIndex = lectureRepository.findMaxOrderIndexBySectionId(sectionId);
        int nextOrderIndex = maxOrderIndex + 1;

        // Create lecture entity
        Lecture lecture = Lecture.builder()
                .section(section)
                .title(request.getTitle())
                .description(request.getDescription())
                .contentType(request.getContentType())
                .videoUrl(request.getVideoUrl())
                .durationSeconds(request.getDurationSeconds())
                .articleContent(request.getArticleContent())
                .resourceUrls(lectureMapper.serializeResourceUrls(request.getResourceUrls()))
                .orderIndex(nextOrderIndex)
                .isPreview(request.getIsPreview() != null ? request.getIsPreview() : false)
                .isCompletedByInstructor(false)
                .build();

        Lecture savedLecture = lectureRepository.save(lecture);

        // Update section's total duration and course totals
        recalculateSectionDuration(sectionId);

        log.info("Lecture created successfully with id: {}", savedLecture.getId());
        return lectureMapper.toResponse(savedLecture);
    }

    /**
     * Get all lectures for a section
     */
    @Override
    public SectionWithLecturesResponse getAllLecturesBySection(
            UUID courseId,
            UUID sectionId,
            UUID userId,
            String userRole,
            boolean isEnrolled
    ) {
        log.debug("Fetching all lectures for section: {} in course: {}", sectionId, courseId);

        // Find section and verify it belongs to the course
        CourseSection section = findSectionAndVerifyCourse(sectionId, courseId);
        Course course = section.getCourse();

        // Fetch all lectures ordered by orderIndex
        List<Lecture> lectures = lectureRepository.findBySectionIdOrderByOrderIndexAsc(sectionId);

        // Filter based on access rights
        List<Lecture> filteredLectures = lectures.stream()
                .filter(lecture -> canAccessLecture(lecture, course, userId, userRole, isEnrolled))
                .collect(Collectors.toList());

        // Temporarily set filtered lectures to section for mapping

        section.setLectures(filteredLectures);

        // Use SectionMapper to build response with lectures
        return lectureMapper.toResponseWithLectures(section);
    }

    /**
     * Get lecture by ID
     */
    @Override
    public LectureResponse getLectureById(
            UUID courseId,
            UUID sectionId,
            UUID lectureId,
            UUID userId,
            String userRole,
            boolean isEnrolled
    ) {
        log.debug("Fetching lecture: {} from section: {} in course: {}", lectureId, sectionId, courseId);

        // Find lecture and verify it belongs to the section
        Lecture lecture = findLectureAndVerifySection(lectureId, sectionId);

        // Verify section belongs to course
        if (!lecture.getSection().getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Lecture not found in the specified course");
        }

        Course course = lecture.getSection().getCourse();

        // Check access permissions
        if (!canAccessLecture(lecture, course, userId, userRole, isEnrolled)) {
            throw new ForbiddenException("You don't have permission to access this lecture");
        }

        return lectureMapper.toResponse(lecture);
    }

    /**
     * Update lecture details
     */
    @Override
    @Transactional
    public LectureResponse updateLecture(
            UUID courseId,
            UUID sectionId,
            UUID lectureId,
            UpdateLectureRequest request,
            String userRole,
            UUID userId
    ) {
        log.info("Updating lecture: {} in section: {} for course: {}", lectureId, sectionId, courseId);

        // Find lecture and verify hierarchy
        Lecture lecture = findLectureAndVerifySection(lectureId, sectionId);

        // Verify section belongs to course
        CourseSection section = lecture.getSection();
        if (!section.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Lecture not found in the specified course");
        }

        // Verify ownership
        verifyCourseOwnership(section.getCourse(), userId, userRole);

        // Validate content if being updated
        validateLectureContentForUpdate(request, lecture.getContentType());

        // Update fields
        boolean durationChanged = false;

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            lecture.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            lecture.setDescription(request.getDescription());
        }
        if (request.getContentType() != null) {
            lecture.setContentType(request.getContentType());
        }
        if (request.getVideoUrl() != null) {
            lecture.setVideoUrl(request.getVideoUrl());
        }
        if (request.getDurationSeconds() != null) {
            lecture.setDurationSeconds(request.getDurationSeconds());
            durationChanged = true;
        }
        if (request.getArticleContent() != null) {
            lecture.setArticleContent(request.getArticleContent());
        }
        if (request.getResourceUrls() != null) {
            lecture.setResourceUrls(lectureMapper.serializeResourceUrls(request.getResourceUrls()));
        }
        if (request.getIsPreview() != null) {
            lecture.setIsPreview(request.getIsPreview());
        }
        if (request.getIsCompletedByInstructor() != null) {
            lecture.setIsCompletedByInstructor(request.getIsCompletedByInstructor());
        }

        Lecture updatedLecture = lectureRepository.save(lecture);

        // Update section duration if duration changed
        if (durationChanged) {
            recalculateSectionDuration(sectionId);
        }

        log.info("Lecture updated successfully: {}", lectureId);
        return lectureMapper.toResponse(updatedLecture);
    }

    /**
     * Delete a lecture
     */
    @Override
    @Transactional
    public void deleteLecture(
            UUID courseId,
            UUID sectionId,
            UUID lectureId,
            String userRole,
            UUID userId
    ) {
        log.info("Deleting lecture: {} from section: {} in course: {}", lectureId, sectionId, courseId);

        // Find lecture and verify hierarchy
        Lecture lecture = findLectureAndVerifySection(lectureId, sectionId);

        // Verify section belongs to course
        CourseSection section = lecture.getSection();
        if (!section.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Lecture not found in the specified course");
        }

        // Verify ownership
        verifyCourseOwnership(section.getCourse(), userId, userRole);

        int deletedOrderIndex = lecture.getOrderIndex();

        // Delete the lecture
        lectureRepository.delete(lecture);

        // Reorder remaining lectures
        reorderLecturesAfterDeletion(sectionId, deletedOrderIndex);

        // Update section and course totals
        recalculateSectionDuration(sectionId);

        log.info("Lecture deleted successfully: {}", lectureId);
    }

    /**
     * Reorder lectures within a section
     */
    @Override
    @Transactional
    public List<LectureResponse> reorderLectures(
            UUID courseId,
            UUID sectionId,
            ReorderLecturesRequest request,
            String userRole,
            UUID userId
    ) {
        log.info("Reordering lectures for section: {} in course: {}", sectionId, courseId);

        // Find section and verify it belongs to course
        CourseSection section = findSectionAndVerifyCourse(sectionId, courseId);

        // Verify ownership
        verifyCourseOwnership(section.getCourse(), userId, userRole);

        // Get requested lecture IDs
        List<UUID> requestedIds = request.getLectureOrders().stream()
                .map(ReorderLecturesRequest.LectureOrderItem::getLectureId)
                .toList();

        Set<UUID> uniqueIds = new HashSet<>(requestedIds);

        if (uniqueIds.size() != requestedIds.size()) {
            throw new InvalidInputException("Duplicate lectureId found in reorder request");
        }

        // Fetch all lectures in the section
        List<Lecture> allLectures = lectureRepository.findBySectionIdOrderByOrderIndexAsc(sectionId);

        if (allLectures.size() != requestedIds.size()) {
            throw new InvalidInputException(
                    "Reorder request must contain all lectures of the section"
            );
        }

        // Create order map
        Map<UUID, Integer> orderMap = request.getLectureOrders().stream()
                .collect(Collectors.toMap(
                        ReorderLecturesRequest.LectureOrderItem::getLectureId,
                        ReorderLecturesRequest.LectureOrderItem::getOrderIndex
                ));

        // Validate all lectures belong to section
        for (Lecture lecture : allLectures) {
            if (!orderMap.containsKey(lecture.getId())) {
                throw new InvalidInputException(
                        "Missing lecture in reorder request: " + lecture.getId()
                );
            }
        }

        // Validate uniqueness & continuity
        Set<Integer> indices = new HashSet<>(orderMap.values());
        int max = allLectures.size() - 1;

        if (indices.size() != allLectures.size()
                || indices.stream().anyMatch(i -> i < 0 || i > max)) {
            throw new InvalidInputException(
                    "Invalid orderIndex values. Must be unique and between 0 and " + max
            );
        }

        // Apply ordering
        allLectures.forEach(lecture ->
                lecture.setOrderIndex(orderMap.get(lecture.getId()))
        );

        lectureRepository.saveAll(allLectures);

        log.info("Lectures reordered successfully for section: {}", sectionId);

        return allLectures.stream()
                .sorted(Comparator.comparing(Lecture::getOrderIndex))
                .map(lectureMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Recalculate section duration (called when lectures are added/updated/deleted)
     */
    @Override
    @Transactional
    public void recalculateSectionDuration(UUID sectionId) {
        CourseSection section = sectionRepository.findByIdWithLectures(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found with id: " + sectionId));

        int totalDuration = section.getLectures().stream()
                .mapToInt(lecture -> lecture.getDurationSeconds() != null ? lecture.getDurationSeconds() : 0)
                .sum();

        section.setTotalDurationSeconds(totalDuration);
        sectionRepository.save(section);

        // Also update course totals
        recalculateCourseTotals(section.getCourse());
    }

    // ==================== Private Helper Methods ====================

    /**
     * Verify course ownership (owner or admin)
     */
    private void verifyCourseOwnership(Course course, UUID userId, String userRole) {
        boolean isOwner = course.getInstructorId().equals(userId);
        boolean isAdmin = "ADMIN".equals(userRole);

        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("You don't have permission to modify this course");
        }
    }

    /**
     * Find section and verify it belongs to the course
     */
    private CourseSection findSectionAndVerifyCourse(UUID sectionId, UUID courseId) {
        CourseSection section = sectionRepository.findByIdWithCourse(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found with id: " + sectionId));

        if (!section.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Section not found in the specified course");
        }

        return section;
    }

    /**
     * Find lecture and verify it belongs to the section
     */
    private Lecture findLectureAndVerifySection(UUID lectureId, UUID sectionId) {
        Lecture lecture = lectureRepository.findByIdAndSectionId(lectureId, sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lecture not found with id: " + lectureId + " in section: " + sectionId
                ));

        return lecture;
    }

    /**
     * Check if user can access a lecture
     * Access Rules:
     * 1. Admin can access everything
     * 2. Course owner can access all lectures
     * 3. Enrolled students can access all lectures of PUBLISHED course
     * 4. Public users can access only preview lectures of PUBLISHED course
     */
    private boolean canAccessLecture(
            Lecture lecture,
            Course course,
            UUID userId,
            String userRole,
            boolean isEnrolled
    ) {
        // Admin has full access
        if ("ADMIN".equals(userRole)) {
            return true;
        }

        // Course owner (instructor) has full access
        if ("INSTRUCTOR".equals(userRole) && course.getInstructorId().equals(userId)) {
            return true;
        }

        System.out.println("LectureID : " + lecture.getId() + " isEnrolled : " + isEnrolled + " isPreview : "
                + lecture.getIsPreview() + " course status : " + course.getStatus() + " isCompletedByInstructor : " + lecture.getIsCompletedByInstructor());

        // For non-owners, course must be published
        if (!CourseStatus.PUBLISHED.equals(course.getStatus())) {
            return false;
        }

        // - Only show lectures marked as completed by instructor
        if (!lecture.getIsCompletedByInstructor()) {
            // Even for enrolled students, hide incomplete lectures
            return false;
        }
        // Enrolled students can access all lectures
        if (isEnrolled) {
            return true;
        }

        // Public users can only access preview lectures
        return lecture.getIsPreview();
    }

    /**
     * Validate lecture content based on content type (CREATE)
     */
    private void validateLectureContent(CreateLectureRequest request) {
        LectureContentType contentType = request.getContentType();

        switch (contentType) {
            case VIDEO:
                validateVideoContent(request.getVideoUrl(), request.getDurationSeconds());
                break;
            case ARTICLE:
                validateArticleContent(request.getArticleContent());
                break;
            case RESOURCE:
                validateResourceContent(request.getResourceUrls());
                break;
            default:
                throw new InvalidInputException("Unsupported content type: " + contentType);
        }
    }

    /**
     * Validate lecture content based on content type (UPDATE)
     */
    private void validateLectureContentForUpdate(
            UpdateLectureRequest request,
            LectureContentType currentContentType
    ) {
        LectureContentType newContentType = request.getContentType() != null
                ? request.getContentType()
                : currentContentType;

        switch (newContentType) {
            case VIDEO:
                if (request.getVideoUrl() != null) {
                    validateVideoContent(request.getVideoUrl(), request.getDurationSeconds());
                }
                break;
            case ARTICLE:
                if (request.getArticleContent() != null) {
                    validateArticleContent(request.getArticleContent());
                }
                break;
            case RESOURCE:
                if (request.getResourceUrls() != null) {
                    validateResourceContent(request.getResourceUrls());
                }
                break;
        }
    }

    /**
     * Validate VIDEO content
     */
    private void validateVideoContent(String videoUrl, Integer durationSeconds) {
        if (!StringUtils.hasText(videoUrl)) {
            throw new InvalidInputException("Video URL is required for VIDEO content type");
        }

        if (!videoUrl.startsWith("http://") && !videoUrl.startsWith("https://")) {
            throw new InvalidInputException("Video URL must be a valid HTTP/HTTPS URL");
        }

        if (durationSeconds == null || durationSeconds <= 0) {
            throw new InvalidInputException(
                    "Duration is required and must be greater than 0 for VIDEO content type"
            );
        }
    }

    /**
     * Validate ARTICLE content
     */
    private void validateArticleContent(String articleContent) {
        if (!StringUtils.hasText(articleContent)) {
            throw new InvalidInputException("Article content is required for ARTICLE content type");
        }

        if (articleContent.length() < 50) {
            throw new InvalidInputException("Article content must be at least 50 characters long");
        }
    }

    /**
     * Validate RESOURCE content
     */
    private void validateResourceContent(List<String> resourceUrls) {
        if (resourceUrls == null || resourceUrls.isEmpty()) {
            throw new InvalidInputException(
                    "At least one resource URL is required for RESOURCE content type"
            );
        }

        for (String url : resourceUrls) {
            if (!StringUtils.hasText(url)) {
                throw new InvalidInputException("Resource URL cannot be empty");
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new InvalidInputException("Resource URL must be a valid HTTP/HTTPS URL: " + url);
            }
        }
    }

    /**
     * Recalculate course totals (duration and lecture count)
     */
    private void recalculateCourseTotals(Course course) {
        List<CourseSection> sections = sectionRepository.findByCourseIdOrderByOrderIndex(course.getId());

        int totalDuration = sections.stream()
                .mapToInt(s -> s.getTotalDurationSeconds() != null ? s.getTotalDurationSeconds() : 0)
                .sum();

        int totalLectures = sections.stream()
                .mapToInt(s -> s.getLectures() != null ? s.getLectures().size() : 0)
                .sum();

        course.setTotalDurationSeconds(totalDuration);
        course.setTotalLectures(totalLectures);

        courseRepository.save(course);
    }

    /**
     * Reorder lectures after deletion
     */
    private void reorderLecturesAfterDeletion(UUID sectionId, int deletedOrderIndex) {
        List<Lecture> lecturesToReorder = lectureRepository.findBySectionIdOrderByOrderIndexAsc(sectionId)
                .stream()
                .filter(l -> l.getOrderIndex() > deletedOrderIndex)
                .collect(Collectors.toList());

        lecturesToReorder.forEach(lecture ->
                lecture.setOrderIndex(lecture.getOrderIndex() - 1)
        );

        if (!lecturesToReorder.isEmpty()) {
            lectureRepository.saveAll(lecturesToReorder);
        }
    }
}