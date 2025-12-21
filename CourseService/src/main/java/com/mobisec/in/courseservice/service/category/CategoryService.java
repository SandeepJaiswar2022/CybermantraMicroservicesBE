package com.mobisec.in.courseservice.service.category;



import com.mobisec.in.courseservice.dto.category.CategoryResponse;
import com.mobisec.in.courseservice.dto.category.CategorySummaryResponse;
import com.mobisec.in.courseservice.dto.category.CreateCategoryRequest;
import com.mobisec.in.courseservice.dto.category.UpdateCategoryRequest;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    /**
     * Create a new category
     */
    CategoryResponse createCategory(CreateCategoryRequest request);

    /**
     * Update an existing category
     */
    CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request);

    /**
     * Get category by ID
     */
    CategoryResponse getCategoryById(UUID categoryId);

    /**
     * Get category by slug
     */
    CategoryResponse getCategoryBySlug(String slug);

    /**
     * Get all parent categories with subcategories
     */
    List<CategoryResponse> getAllParentCategories();

    /**
     * Get all categories (flat list)
     */
    List<CategoryResponse> getAllCategories();

    /**
     * Get all active categories summary (for dropdowns)
     */
    List<CategorySummaryResponse> getAllCategoriesSummary();

    /**
     * Get subcategories of a parent category
     */
    List<CategoryResponse> getSubcategories(UUID parentId);

    /**
     * Soft delete a category
     */
    void deleteCategory(UUID categoryId);

    /**
     * Activate/Deactivate a category
     */
    CategoryResponse toggleCategoryStatus(UUID categoryId);
}
