package com.mobisec.in.courseservice.repository;


import com.mobisec.in.courseservice.entity.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LectureRepository extends JpaRepository<Lecture, UUID> {

    /**
     * Find all lectures by section ID, ordered by orderIndex
     */
    List<Lecture> findBySectionIdOrderByOrderIndexAsc(UUID sectionId);

    /**
     * Find lecture by ID and section ID (for validation)
     */
    Optional<Lecture> findByIdAndSectionId(UUID lectureId, UUID sectionId);

    /**
     * Check if order index already exists within a section (excluding current lecture)
     */
    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END " +
            "FROM Lecture l WHERE l.section.id = :sectionId " +
            "AND l.orderIndex = :orderIndex " +
            "AND l.id != :excludeLectureId")
    boolean existsByOrderIndexInSection(
            @Param("sectionId") UUID sectionId,
            @Param("orderIndex") Integer orderIndex,
            @Param("excludeLectureId") UUID excludeLectureId
    );

    /**
     * Check if order index exists when creating new lecture
     */
    boolean existsBySectionIdAndOrderIndex(UUID sectionId, Integer orderIndex);

    /**
     * Get maximum order index in a section
     */
    @Query("SELECT COALESCE(MAX(l.orderIndex), -1) FROM Lecture l WHERE l.section.id = :sectionId")
    Integer findMaxOrderIndexBySectionId(@Param("sectionId") UUID sectionId);

    /**
     * Count lectures in a section
     */
    long countBySectionId(UUID sectionId);

    /**
     * Find all preview lectures in a section
     */
    List<Lecture> findBySectionIdAndIsPreviewTrue(UUID sectionId);

    /**
     * Check if all lectures in a section are marked as completed by instructor
     */
    @Query("SELECT CASE WHEN COUNT(l) = 0 THEN true ELSE false END " +
            "FROM Lecture l WHERE l.section.id = :sectionId " +
            "AND l.isCompletedByInstructor = false")
    boolean areAllLecturesCompletedInSection(@Param("sectionId") UUID sectionId);

    /**
     * Calculate total duration of all lectures in a section
     */
    @Query("SELECT COALESCE(SUM(l.durationSeconds), 0) FROM Lecture l WHERE l.section.id = :sectionId")
    Integer calculateTotalDurationBySectionId(@Param("sectionId") UUID sectionId);

    /**
     * Delete all lectures by section ID (cascade scenario)
     */
    void deleteBySectionId(UUID sectionId);

    /**
     * Find incomplete lectures by section ID
     */
    @Query("SELECT l FROM Lecture l WHERE l.section.id = :sectionId " +
            "AND l.isCompletedByInstructor = false")
    List<Lecture> findIncompleteLecturesBySectionId(@Param("sectionId") UUID sectionId);

    boolean existsBySection_IdAndTitleIgnoreCase(UUID sectionId, String title);
}
