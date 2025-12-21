package com.mobisec.in.courseservice.dto.category;


import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCategoryRequest {

    @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Size(max = 500, message = "Icon URL cannot exceed 500 characters")
    private String iconUrl;

    private UUID parentId;

    private Boolean isActive;
}