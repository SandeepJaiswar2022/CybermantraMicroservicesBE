package com.mobisec.in.courseservice.dto.common;

import lombok.*;

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
    private Long timestamp;
}
