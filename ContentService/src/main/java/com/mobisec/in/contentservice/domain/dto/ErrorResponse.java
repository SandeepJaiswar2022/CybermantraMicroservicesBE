package com.mobisec.in.contentservice.domain.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private String error;
    private String message;
    private Integer status;
    private String path;
    private LocalDateTime timestamp;
}
