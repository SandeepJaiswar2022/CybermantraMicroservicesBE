package com.mobisec.in.courseservice.service.category;


import com.mobisec.in.courseservice.dto.category.CategoryResponse;
import com.mobisec.in.courseservice.dto.category.CategorySummaryResponse;
import com.mobisec.in.courseservice.dto.category.CreateCategoryRequest;
import com.mobisec.in.courseservice.dto.category.UpdateCategoryRequest;
import com.mobisec.in.courseservice.entity.Category;
import com.mobisec.in.courseservice.exception.DuplicateResourceException;
import com.mobisec.in.courseservice.exception.InvalidOperationException;
import com.mobisec.in.courseservice.exception.ResourceNotFoundException;
import com.mobisec.in.courseservice.exception.UnauthorizedException;
import com.mobisec.in.courseservice.mapper.CategoryMapper;
import com.mobisec.in.courseservice.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.info("Creating category with name: {}", request.getName());

        // Authorization check - Only ADMIN can create categories
//        validateAdminRole(userRole);

        // Validate category name uniqueness
        if (categoryRepository.existsByNameIgnoreCaseAndIsDeletedFalse(request.getName())) {
            throw new DuplicateResourceException("Category with name '" + request.getName() + "' already exists");
        }

        // Generate slug from name
        String slug = generateSlug(request.getName());

        // Validate slug uniqueness
        if (categoryRepository.existsBySlugAndIsDeletedFalse(slug)) {
            throw new DuplicateResourceException("Category with slug '" + slug + "' already exists");
        }

        // Build category entity
        Category category = Category.builder()
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .iconUrl(request.getIconUrl())
                .isActive(true)
                .isDeleted(false)
                .build();

        // Handle parent category for subcategory
        if (request.getParentId() != null) {
            Category parent = categoryRepository.findByIdAndIsDeletedFalse(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with ID: " + request.getParentId()));

            // Validate parent doesn't have a parent (only 2 levels allowed)
            if (parent.getParent() != null) {
                throw new InvalidOperationException("Cannot create subcategory of a subcategory. Only 2-level hierarchy is allowed.");
            }

            category.setParent(parent);
            log.info("Creating subcategory under parent: {}", parent.getName());
        }

        // Save category
        Category savedCategory = categoryRepository.save(category);
        log.info("Category created successfully with ID: {}", savedCategory.getId());

        // Get course count (will be 0 for new category)
        Integer courseCount = categoryRepository.countCoursesByCategoryId(savedCategory.getId());

        return categoryMapper.toCategoryResponse(savedCategory, courseCount);
    }

    @Override
    public CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request) {
        log.info("Updating category ID: {}", categoryId);

        // Find existing category
        Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));

        // Update name if provided
        if (request.getName() != null && !request.getName().isBlank()) {
            String trimmedName = request.getName().trim();

            // Check if name is changed and validate uniqueness
            if (!category.getName().equalsIgnoreCase(trimmedName)) {
                if (categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, categoryId)) {
                    throw new DuplicateResourceException("Category with name '" + trimmedName + "' already exists");
                }

                // Update name and regenerate slug
                category.setName(trimmedName);
                String newSlug = generateSlug(trimmedName);

                // Validate new slug uniqueness
                if (categoryRepository.existsBySlugAndIdNot(newSlug, categoryId)) {
                    throw new DuplicateResourceException("Category with slug '" + newSlug + "' already exists");
                }

                category.setSlug(newSlug);
                log.info("Updated category name to: {} and slug to: {}", trimmedName, newSlug);
            }
        }

        // Update description
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        // Update icon URL
        if (request.getIconUrl() != null) {
            category.setIconUrl(request.getIconUrl());
        }

        // Update active status
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
            log.info("Category active status changed to: {}", request.getIsActive());
        }

        // Update parent if provided
        if (request.getParentId() != null) {
            Category newParent = categoryRepository.findByIdAndIsDeletedFalse(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with ID: " + request.getParentId()));

            // Validate parent doesn't have a parent
            if (newParent.getParent() != null) {
                throw new InvalidOperationException("Cannot set a subcategory as parent. Only 2-level hierarchy is allowed.");
            }

            // Validate not setting self as parent
            if (newParent.getId().equals(categoryId)) {
                throw new InvalidOperationException("Category cannot be its own parent");
            }

            // Validate this category has no subcategories if being moved under a parent
            if (!category.getSubcategories().isEmpty()) {
                throw new InvalidOperationException("Cannot convert a parent category with subcategories into a subcategory");
            }

            category.setParent(newParent);
            log.info("Updated parent category to: {}", newParent.getName());
        }

        // Save updated category
        Category updatedCategory = categoryRepository.save(category);
        log.info("Category updated successfully");

        // Get course count
        Integer courseCount = categoryRepository.countCoursesByCategoryId(updatedCategory.getId());

        return categoryMapper.toCategoryResponse(updatedCategory, courseCount);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID categoryId) {
        log.info("Fetching category by ID: {}", categoryId);

        Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));

        Integer courseCount = categoryRepository.countCoursesByCategoryId(categoryId);

        return categoryMapper.toCategoryResponseWithSubcategories(category, courseCount);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug) {
        log.info("Fetching category by slug: {}", slug);

        Category category = categoryRepository.findBySlugAndIsDeletedFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with slug: " + slug));

        Integer courseCount = categoryRepository.countCoursesByCategoryId(category.getId());

        return categoryMapper.toCategoryResponseWithSubcategories(category, courseCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllParentCategories() {
        log.info("Fetching all parent categories with subcategories");

        List<Category> parentCategories = categoryRepository.findAllParentCategories();

        return parentCategories.stream()
                .map(category -> {
                    Integer courseCount = categoryRepository.countCoursesByCategoryId(category.getId());
                    return categoryMapper.toCategoryResponseWithSubcategories(category, courseCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        log.info("Fetching all categories (flat list)");

        List<Category> categories = categoryRepository.findAllByIsDeletedFalseOrderByNameAsc();

        return categories.stream()
                .map(category -> {
                    Integer courseCount = categoryRepository.countCoursesByCategoryId(category.getId());
                    return categoryMapper.toCategoryResponse(category, courseCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategorySummaryResponse> getAllCategoriesSummary() {
        log.info("Fetching all categories summary");

        List<Category> categories = categoryRepository.findAllByIsDeletedFalseOrderByNameAsc();

        return categoryMapper.toCategorySummaryResponseList(categories);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getSubcategories(UUID parentId) {
        log.info("Fetching subcategories for parent ID: {}", parentId);

        // Validate parent exists
        Category parent = categoryRepository.findByIdAndIsDeletedFalse(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with ID: " + parentId));

        // Validate it's actually a parent (has no parent itself)
        if (parent.getParent() != null) {
            throw new InvalidOperationException("Category with ID " + parentId + " is not a parent category");
        }

        List<Category> subcategories = categoryRepository.findSubcategoriesByParentId(parentId);

        return subcategories.stream()
                .map(category -> {
                    Integer courseCount = categoryRepository.countCoursesByCategoryId(category.getId());
                    return categoryMapper.toCategoryResponse(category, courseCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    public void deleteCategory(UUID categoryId) {
        log.info("Deleting category ID: {}", categoryId);

        // Find category
        Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));

        // Check if category has active courses
        Integer courseCount = categoryRepository.countCoursesByCategoryId(categoryId);
        if (courseCount > 0) {
            throw new InvalidOperationException(
                    "Cannot delete category with " + courseCount + " active courses. Please reassign or delete courses first.");
        }

        // Check if category has subcategories
        if (!category.getSubcategories().isEmpty()) {
            long activeSubcategories = category.getSubcategories().stream()
                    .filter(sub -> !sub.getIsDeleted())
                    .count();

            if (activeSubcategories > 0) {
                throw new InvalidOperationException(
                        "Cannot delete parent category with " + activeSubcategories + " active subcategories. Please delete subcategories first.");
            }
        }

        // Perform soft delete
        category.setIsDeleted(true);
        category.setDeletedAt(LocalDateTime.now());
        category.setIsActive(false);

        categoryRepository.save(category);
        log.info("Category soft deleted successfully");
    }

    @Override
    public CategoryResponse toggleCategoryStatus(UUID categoryId) {
        log.info("Toggling status for category ID: {}", categoryId);



        // Find category
        Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));

        // Toggle active status
        category.setIsActive(!category.getIsActive());

        // If deactivating a parent, also deactivate subcategories
        if (!category.getIsActive() && category.getSubcategories() != null) {
            category.getSubcategories().forEach(sub -> sub.setIsActive(false));
            log.info("Deactivated {} subcategories", category.getSubcategories().size());
        }

        Category updatedCategory = categoryRepository.save(category);
        log.info("Category status toggled to: {}", updatedCategory.getIsActive());

        Integer courseCount = categoryRepository.countCoursesByCategoryId(categoryId);

        return categoryMapper.toCategoryResponse(updatedCategory, courseCount);
    }

    /**
     * Generate URL-friendly slug from category name
     */
    private String generateSlug(String name) {
        return name.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "") // Remove special characters
                .replaceAll("\\s+", "-")          // Replace spaces with hyphens
                .replaceAll("-+", "-")            // Replace multiple hyphens with single
                .replaceAll("^-|-$", "");         // Remove leading/trailing hyphens
    }

    /**
     * Validate user has ADMIN role
     */
    private void validateAdminRole(String userRole) {
        if (userRole == null || !userRole.equalsIgnoreCase("ADMIN")) {
            throw new UnauthorizedException("Only administrators can perform this operation");
        }
    }
}
