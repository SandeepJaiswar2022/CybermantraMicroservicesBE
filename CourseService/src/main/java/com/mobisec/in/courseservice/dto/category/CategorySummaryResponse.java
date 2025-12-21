package com.mobisec.in.courseservice.dto.category;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorySummaryResponse {

    private UUID id;
    private String name;
    private String slug;
    private String iconUrl;
}