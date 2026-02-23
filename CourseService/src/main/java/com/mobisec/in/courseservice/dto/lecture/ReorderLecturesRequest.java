package com.mobisec.in.courseservice.dto.lecture;

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
public class ReorderLecturesRequest {

    @NotEmpty(message = "Lecture order list cannot be empty")
    private List<LectureOrderItem> lectureOrders;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LectureOrderItem {
        @NotNull(message = "Lecture ID is required")
        private UUID lectureId;

        @NotNull(message = "Order index is required")
        private Integer orderIndex;
    }
}

