package com.mobisec.in.courseservice.controller;



import com.mobisec.in.courseservice.dto.category.CategoryResponse;
import com.mobisec.in.courseservice.dto.category.CategorySummaryResponse;
import com.mobisec.in.courseservice.dto.category.CreateCategoryRequest;
import com.mobisec.in.courseservice.dto.category.UpdateCategoryRequest;
import com.mobisec.in.courseservice.dto.common.ApiResponse;
import com.mobisec.in.courseservice.service.category.CategoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Create a new category
     * Access: ADMIN only
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("POST /api/v1/categories - Creating category by user: {} with role: {}", userId, userRole);

        CategoryResponse response = categoryService.createCategory(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", response));
    }


    /**
     * Update an existing category
     * Access: ADMIN only
     */
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("PUT /api/v1/categories/{} - Updating category by user: {}", categoryId, userId);

        CategoryResponse response = categoryService.updateCategory(categoryId, request);

        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", response));
    }

    /**
     * Delete a category (soft delete)
     * Access: ADMIN only
     */
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable UUID categoryId,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("DELETE /api/v1/categories/{} by user: {}", categoryId, userId);

        categoryService.deleteCategory(categoryId);

        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }

    /**
     * Toggle category active status
     * Access: ADMIN only
     */
    @PatchMapping("/{categoryId}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> toggleCategoryStatus(
            @PathVariable UUID categoryId,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("PATCH /api/v1/categories/{}/toggle-status by user: {}", categoryId, userId);

        CategoryResponse response = categoryService.toggleCategoryStatus(categoryId);

        return ResponseEntity.ok(ApiResponse.success("Category status toggled successfully", response));
    }

    // ============= PUBLIC ENDPOINTS (No Authentication Required) =============

    /**
     * Get category by ID
     * Access: Public
     */
    @GetMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(@PathVariable UUID categoryId) {
        log.info("GET /api/v1/categories/{}", categoryId);

        CategoryResponse response = categoryService.getCategoryById(categoryId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get category by slug
     * Access: Public
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryBySlug(@PathVariable String slug) {
        log.info("GET /api/v1/categories/slug/{}", slug);

        CategoryResponse response = categoryService.getCategoryBySlug(slug);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all parent categories with their subcategories
     * Access: Public
     */
    @GetMapping("/parents")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllParentCategories() {
        log.info("GET /api/v1/categories/parents");

        List<CategoryResponse> responses = categoryService.getAllParentCategories();

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * Get all categories (flat list)
     * Access: Public
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        log.info("GET /api/v1/categories");

        List<CategoryResponse> responses = categoryService.getAllCategories();

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * Get all categories summary (for dropdowns)
     * Access: Public
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<List<CategorySummaryResponse>>> getAllCategoriesSummary() {
        log.info("GET /api/v1/categories/summary");

        List<CategorySummaryResponse> responses = categoryService.getAllCategoriesSummary();

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * Get subcategories of a parent category
     * Access: Public
     */
    @GetMapping("/{parentId}/subcategories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getSubcategories(@PathVariable UUID parentId) {
        log.info("GET /api/v1/categories/{}/subcategories", parentId);

        List<CategoryResponse> responses = categoryService.getSubcategories(parentId);

        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
