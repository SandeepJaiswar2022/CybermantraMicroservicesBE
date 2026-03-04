package com.lms.content.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

/**
 * Standard API response envelope used across all endpoints.
 *
 * <p>Consistent structure makes frontend handling predictable and simplifies
 * API gateway response parsing.
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Upload successful",
 *   "data": { ... },
 *   "timestamp": "2024-01-01T12:00:00Z"
 * }
 * </pre>
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final String error;
    private final Instant timestamp;

    private ApiResponse(boolean success, String message, T data, String error) {
        this.success   = success;
        this.message   = message;
        this.data      = data;
        this.error     = error;
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(false, null, null, error);
    }

    public static <T> ApiResponse<T> error(String message, String error) {
        return new ApiResponse<>(false, message, null, error);
    }
}
