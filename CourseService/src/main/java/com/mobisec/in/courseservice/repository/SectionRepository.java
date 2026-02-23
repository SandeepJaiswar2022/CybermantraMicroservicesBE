package com.mobisec.in.courseservice.repository;


import com.mobisec.in.courseservice.entity.CourseSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SectionRepository extends JpaRepository<CourseSection, UUID> {

    /**
     * Find all sections for a specific course, ordered by orderIndex
     */
    @Query("SELECT s FROM CourseSection s WHERE s.course.id = :courseId AND s.course.isDeleted = false ORDER BY s.orderIndex ASC")
    List<CourseSection> findByCourseIdOrderByOrderIndex(@Param("courseId") UUID courseId);

    /**
     * Find section by ID with course details (to avoid N+1 queries)
     */
    @Query("SELECT s FROM CourseSection s JOIN FETCH s.course WHERE s.id = :sectionId")
    Optional<CourseSection> findByIdWithCourse(@Param("sectionId") UUID sectionId);

    /**
     * Find section by ID with course and lectures (for detailed view)
     */
    @Query("SELECT DISTINCT s FROM CourseSection s " +
            "LEFT JOIN FETCH s.lectures l " +
            "JOIN FETCH s.course c " +
            "WHERE s.id = :sectionId " +
            "ORDER BY l.orderIndex ASC")
    Optional<CourseSection> findByIdWithLectures(@Param("sectionId") UUID sectionId);

    /**
     * Get the maximum order index for a course (used when adding new sections)
     */
    @Query("SELECT COALESCE(MAX(s.orderIndex), -1) FROM CourseSection s WHERE s.course.id = :courseId")
    Integer findMaxOrderIndexByCourseId(@Param("courseId") UUID courseId);

    /**
     * Count sections in a course
     */
    @Query("SELECT COUNT(s) FROM CourseSection s WHERE s.course.id = :courseId")
    Long countByCourseId(@Param("courseId") UUID courseId);

    /**
     * Check if section exists and belongs to specific course
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
            "FROM CourseSection s WHERE s.id = :sectionId AND s.course.id = :courseId")
    boolean existsByIdAndCourseId(@Param("sectionId") UUID sectionId, @Param("courseId") UUID courseId);
}
