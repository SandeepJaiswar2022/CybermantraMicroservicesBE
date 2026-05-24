package com.mobisec.in.courseservice.service.section;


import com.mobisec.in.courseservice.dto.section.*;
import com.mobisec.in.courseservice.entity.Course;
import com.mobisec.in.courseservice.entity.CourseSection;
import com.mobisec.in.courseservice.exception.ForbiddenException;
import com.mobisec.in.courseservice.exception.InvalidInputException;
import com.mobisec.in.courseservice.exception.ResourceNotFoundException;
import com.mobisec.in.courseservice.mapper.SectionMapper;
import com.mobisec.in.courseservice.repository.CourseRepository;
import com.mobisec.in.courseservice.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SectionServiceImpl implements SectionService {

    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;
    private final SectionMapper sectionMapper;

    /**
     * Create a new section for a course
     */
    @Override
    @Transactional
    public SectionResponse createSection(UUID courseId, CreateSectionRequest request, String userRole, UUID userId) {
        log.info("Creating section for course: {}", courseId);

        Course course = findCourseAndVerifyOwnership(courseId, userId, userRole);

        // Get the next order index
        Integer maxOrderIndex = sectionRepository.findMaxOrderIndexByCourseId(courseId);
        int nextOrderIndex = maxOrderIndex + 1;

        CourseSection section = CourseSection.builder()
                .course(course)
                .title(request.getTitle())
                .description(request.getDescription())
                .objective(request.getObjective())
                .orderIndex(nextOrderIndex)
                .totalDurationSeconds(0)
                .build();

        CourseSection savedSection = sectionRepository.save(section);

        // Update course total sections count
        updateCourseSectionCount(course);

        log.info("Section created successfully with id: {}", savedSection.getId());
        return sectionMapper.toResponse(savedSection);
    }

    /**
     * Get all sections for a course
     */
    @Override
    public List<SectionResponse> getAllSectionsByCourse(UUID courseId) {
        log.debug("Fetching all sections for course: {}", courseId);

        // Verify course exists
        courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));

        List<CourseSection> sections = sectionRepository.findByCourseIdOrderByOrderIndex(courseId);
        return sections.stream()
                .map(sectionMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get section by ID with basic details
     */
    @Override
    public SectionResponse getSectionById(UUID courseId, UUID sectionId) {
        log.debug("Fetching section: {} for course: {}", sectionId, courseId);

        CourseSection section = findSectionAndVerifyCourse(sectionId, courseId);
        return sectionMapper.toResponse(section);
    }


    /**
     * Update section details
     */
    @Override
    @Transactional
    public SectionResponse updateSection(UUID courseId, UUID sectionId, UpdateSectionRequest request, String userRole, UUID userId) {
        log.info("Updating section: {} for course: {}", sectionId, courseId);

        findCourseAndVerifyOwnership(courseId, userId, userRole);
        CourseSection section = findSectionAndVerifyCourse(sectionId, courseId);

        // Update fields
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            section.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            section.setDescription(request.getDescription());
        }
        if (request.getObjective() != null) {
            section.setObjective(request.getObjective());
        }
        CourseSection updatedSection = sectionRepository.save(section);
        log.info("Section updated successfully: {}", sectionId);

        return sectionMapper.toResponse(updatedSection);
    }

    /**
     * Delete a section (cascade delete lectures)
     */
    @Override
    @Transactional
    public void deleteSection(UUID courseId, UUID sectionId, String userRole, UUID userId) {
        log.info("Deleting section: {} from course: {}", sectionId, courseId);

        Course course = findCourseAndVerifyOwnership(courseId, userId, userRole);
        CourseSection section = findSectionAndVerifyCourse(sectionId, courseId);

        int deletedOrderIndex = section.getOrderIndex();
        // Delete the section (will cascade to lectures due to orphanRemoval)
        sectionRepository.delete(section);

        // Reorder remaining sections
        reorderSectionsAfterDeletion(courseId, deletedOrderIndex);

        // Update course metadata
        updateCourseSectionCount(course);
        recalculateCourseTotals(course);

        log.info("Section deleted successfully: {}", sectionId);
    }

    /**
     * Reorder sections
     */

    @Override
    @Transactional
    public List<SectionResponse> reorderSections(
            UUID courseId,
            ReorderSectionsRequest request,
            String userRole,
            UUID userId) {

        log.info("Reordering sections for course: {}", courseId);

        findCourseAndVerifyOwnership(courseId, userId, userRole);

        List<UUID> requestedIds = request.getSectionOrders().stream()
                .map(ReorderSectionsRequest.SectionOrder::getSectionId)
                .toList();

        Set<UUID> uniqueIds = new HashSet<>(requestedIds);

        if (uniqueIds.size() != requestedIds.size()) {
            throw new InvalidInputException("Duplicate sectionId found in reorder request");
        }

        List<CourseSection> allSections =
                sectionRepository.findByCourseIdOrderByOrderIndex(courseId);

        if (allSections.size() != requestedIds.size()) {
            throw new InvalidInputException(
                    "Reorder request must contain all sections of the course"
            );
        }


        Map<UUID, Integer> orderMap =
                request.getSectionOrders().stream()
                        .collect(Collectors.toMap(
                                ReorderSectionsRequest.SectionOrder::getSectionId,
                                ReorderSectionsRequest.SectionOrder::getOrderIndex
                        ));

        // Validate all sections belong to course
        for (CourseSection section : allSections) {
            if (!orderMap.containsKey(section.getId())) {
                throw new InvalidInputException(
                        "Missing section in reorder request: " + section.getId()
                );
            }
        }

        // Validate uniqueness & continuity
        Set<Integer> indices = new HashSet<>(orderMap.values());
        int max = allSections.size() - 1;

        if (indices.size() != allSections.size()
                || indices.stream().anyMatch(i -> i < 0 || i > max)) {
            throw new InvalidInputException(
                    "Invalid orderIndex values. Must be unique and between 0 and " + max
            );
        }

        // Apply ordering
        allSections.forEach(section ->
                section.setOrderIndex(orderMap.get(section.getId()))
        );

        sectionRepository.saveAll(allSections);

        log.info("Sections reordered successfully for course: {}", courseId);

        return allSections.stream()
                .sorted(Comparator.comparing(CourseSection::getOrderIndex))
                .map(sectionMapper::toResponse)
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

    private Course findCourseAndVerifyOwnership(UUID courseId, UUID userId, String userRole) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));

        boolean isOwner = course.getInstructorId().equals(userId);
        boolean isAdmin = "ADMIN".equals(userRole);

        if (!isOwner && !isAdmin) {

            throw new ForbiddenException("You don't have permission to modify this course");
        }

        return course;
    }

    private CourseSection findSectionAndVerifyCourse(UUID sectionId, UUID courseId) {
        CourseSection section = sectionRepository.findByIdWithCourse(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found with id: " + sectionId));

        if (!section.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Section not found in the specified course");
        }

        return section;
    }

    private void updateCourseSectionCount(Course course) {
        Long sectionCount = sectionRepository.countByCourseId(course.getId());
        course.setTotalSections(sectionCount.intValue());
        courseRepository.save(course);
    }

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
        course.setTotalSections(sections.size());

        courseRepository.save(course);
    }

    private void reorderSectionsAfterDeletion(UUID courseId, int deletedOrderIndex) {
        List<CourseSection> sectionsToReorder = sectionRepository.findByCourseIdOrderByOrderIndex(courseId)
                .stream()
                .filter(s -> s.getOrderIndex() > deletedOrderIndex)
                .collect(Collectors.toList());

        sectionsToReorder.forEach(section ->
                section.setOrderIndex(section.getOrderIndex() - 1)
        );

        if (!sectionsToReorder.isEmpty()) {
            sectionRepository.saveAll(sectionsToReorder);
        }
    }
}
