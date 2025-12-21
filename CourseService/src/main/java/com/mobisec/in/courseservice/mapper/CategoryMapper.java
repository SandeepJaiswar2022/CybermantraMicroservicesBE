package com.mobisec.in.courseservice.mapper;


import com.mobisec.in.courseservice.dto.category.CategoryResponse;
import com.mobisec.in.courseservice.dto.category.CategorySummaryResponse;
import com.mobisec.in.courseservice.entity.Category;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CategoryMapper {

    /**
     * Convert Category entity to CategoryResponse DTO
     */
    public CategoryResponse toCategoryResponse(Category category, Integer courseCount) {
        if (category == null) {
            return null;
        }

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .iconUrl(category.getIconUrl())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .isActive(category.getIsActive())
                .courseCount(courseCount != null ? courseCount : 0)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .subcategories(new ArrayList<>()) // Will be populated separately if needed
                .build();
    }

    /**
     * Convert Category entity to CategoryResponse with subcategories
     */
    public CategoryResponse toCategoryResponseWithSubcategories(Category category, Integer courseCount) {
        CategoryResponse response = toCategoryResponse(category, courseCount);

        if (category.getSubcategories() != null && !category.getSubcategories().isEmpty()) {
            List<CategoryResponse> subcategoryResponses = category.getSubcategories().stream()
                    .filter(sub -> !sub.getIsDeleted())
                    .map(sub -> toCategoryResponse(sub, 0)) // Subcategories without course count
                    .collect(Collectors.toList());
            response.setSubcategories(subcategoryResponses);
        }

        return response;
    }

    /**
     * Convert Category entity to CategorySummaryResponse
     */
    public CategorySummaryResponse toCategorySummaryResponse(Category category) {
        if (category == null) {
            return null;
        }

        return CategorySummaryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .iconUrl(category.getIconUrl())
                .build();
    }

    /**
     * Convert list of categories to summary responses
     */
    public List<CategorySummaryResponse> toCategorySummaryResponseList(List<Category> categories) {
        return categories.stream()
                .map(this::toCategorySummaryResponse)
                .collect(Collectors.toList());
    }
}
