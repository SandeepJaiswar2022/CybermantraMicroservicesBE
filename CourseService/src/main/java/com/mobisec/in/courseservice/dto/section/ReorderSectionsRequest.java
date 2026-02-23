package com.mobisec.in.courseservice.dto.section;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderSectionsRequest {

    @NotEmpty(message = "Section order list cannot be empty")
    private List<SectionOrder> sectionOrders;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SectionOrder {
        @NotNull(message = "Section ID is required")
        private UUID sectionId;

        @NotNull(message = "Order index is required")
        @Min(value = 0, message = "Order index must be zero or positive")
        private Integer orderIndex;
    }
}