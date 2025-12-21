package com.mobisec.in.courseservice.dto.category;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String iconUrl;
    private UUID parentId;
    private String parentName;
    private Boolean isActive;
    private Integer courseCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<CategoryResponse> subcategories = new ArrayList<>();
}