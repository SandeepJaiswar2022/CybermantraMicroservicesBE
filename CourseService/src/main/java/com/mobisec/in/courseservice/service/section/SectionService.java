package com.mobisec.in.courseservice.service.section;




import com.mobisec.in.courseservice.dto.section.*;

import java.util.List;
import java.util.UUID;

public interface SectionService {

    /**
     * Create a new section for a course
     */
    SectionResponse createSection(UUID courseId, CreateSectionRequest request,String userRole, UUID userId);

    /**
     * Get all sections for a course
     */
    List<SectionResponse> getAllSectionsByCourse(UUID courseId);

    /**
     * Get section by ID with basic details
     */
    SectionResponse getSectionById(UUID courseId, UUID sectionId);



    /**
     * Update section details
     */
    SectionResponse updateSection(UUID courseId, UUID sectionId, UpdateSectionRequest request, String userRole, UUID userId);

    /**
     * Delete a section (cascade delete lectures)
     */
    void deleteSection(UUID courseId, UUID sectionId, String userRole, UUID userId);

    /**
     * Reorder sections within a course
     */
    List<SectionResponse> reorderSections(UUID courseId, ReorderSectionsRequest request, String userRole, UUID userId);

    /**
     * Recalculate section duration when lectures change
     */
    void recalculateSectionDuration(UUID sectionId);
}
