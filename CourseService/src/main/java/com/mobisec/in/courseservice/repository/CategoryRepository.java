package com.mobisec.in.courseservice.repository;

import com.mobisec.in.courseservice.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Find category by slug
     */
    Optional<Category> findBySlugAndIsDeletedFalse(String slug);

    /**
     * Find all active parent categories (categories without parent)
     */
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.isDeleted = false ORDER BY c.name ASC")
    List<Category> findAllParentCategories();

    /**
     * Find all active subcategories of a parent
     */
    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.isDeleted = false ORDER BY c.name ASC")
    List<Category> findSubcategoriesByParentId(@Param("parentId") UUID parentId);

    /**
     * Find all active categories
     */
    List<Category> findAllByIsDeletedFalseOrderByNameAsc();

    /**
     * Check if category name exists (excluding current category for updates)
     */
    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE LOWER(c.name) = LOWER(:name) AND c.isDeleted = false AND c.id != :excludeId")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("excludeId") UUID excludeId);

    /**
     * Check if category name exists
     */
    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    /**
     * Check if slug exists (excluding current category)
     */
    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.slug = :slug AND c.isDeleted = false AND c.id != :excludeId")
    boolean existsBySlugAndIdNot(@Param("slug") String slug, @Param("excludeId") UUID excludeId);

    /**
     * Check if slug exists
     */
    boolean existsBySlugAndIsDeletedFalse(String slug);

    /**
     * Find category by ID (not deleted)
     */
    Optional<Category> findByIdAndIsDeletedFalse(UUID id);

    /**
     * Count courses in a category
     */
    @Query("SELECT COUNT(course) FROM Course course WHERE course.category.id = :categoryId AND course.isDeleted = false")
    Integer countCoursesByCategoryId(@Param("categoryId") UUID categoryId);
}