package com.mobisec.in.contentservice.exception;


import com.mobisec.in.contentservice.domain.dto.ApiResponse;
import com.mobisec.in.contentservice.domain.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.*;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─────────────────────────────────────────────
    // Resource Not Found
    // ─────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {

        log.error("Resource not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ─────────────────────────────────────────────
    // Invalid Upload
    // ─────────────────────────────────────────────

    @ExceptionHandler(InvalidUploadException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvalidUploadException(
            InvalidUploadException ex) {

        log.error("Invalid upload: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ─────────────────────────────────────────────
    // Storage Errors
    // ─────────────────────────────────────────────

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleStorageException(
            StorageException ex) {

        log.error("Storage service error: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Storage service temporarily unavailable. Please retry later."
                ));
    }

    // ─────────────────────────────────────────────
    // Forbidden
    // ─────────────────────────────────────────────

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(
            ForbiddenException ex) {

        log.error("Forbidden access: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ─────────────────────────────────────────────
    // Spring Security AccessDenied
    // ─────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex) {

        log.error("Access denied: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You don't have permission to access this resource"));
    }

    // ─────────────────────────────────────────────
    // Validation Errors
    // ─────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        log.warn(
                "Validation failed for request [{}] with errors: {}",
                request.getRequestURI(),
                errors
        );

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Validation failed", errors));
    }

    // ─────────────────────────────────────────────
    // Parameter Type Mismatch
    // ─────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String paramName = ex.getName();
        Object rawValue = ex.getValue();
        String invalidValue = rawValue == null ? "null" : rawValue.toString();
        Class<?> requiredType = ex.getRequiredType();

        String message;

        if (requiredType != null && requiredType.isEnum()) {

            String allowedValues = Arrays.stream(requiredType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));

            message = String.format(
                    "Invalid value '%s' for parameter '%s'. Allowed values are: [%s]",
                    invalidValue, paramName, allowedValues
            );

        } else if (requiredType == UUID.class) {

            message = String.format(
                    "Invalid UUID '%s' for parameter '%s'. Expected valid UUID format",
                    invalidValue, paramName
            );

        } else {

            message = String.format(
                    "Invalid value '%s' for parameter '%s'",
                    invalidValue, paramName
            );
        }

        log.error("Parameter type mismatch: {}", message);

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, null));
    }

    // ─────────────────────────────────────────────
    // Malformed JSON
    // ─────────────────────────────────────────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {

        log.error("Malformed JSON request: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        "Request body is missing or malformed JSON"
                ));
    }

    // ─────────────────────────────────────────────
    // Unsupported Media Type
    // ─────────────────────────────────────────────

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {

        log.error("Unsupported media type: {}", ex.getContentType());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Content-Type must be application/json"));
    }

    // ─────────────────────────────────────────────
    // Endpoint Not Found
    // ─────────────────────────────────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {

        log.error("Invalid endpoint accessed: {}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        "Invalid API endpoint. Please verify the request path."
                ));
    }

    // ─────────────────────────────────────────────
    // Method Not Supported
    // ─────────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {

        String supportedMethods = ex.getSupportedHttpMethods() != null
                ? ex.getSupportedHttpMethods().stream()
                .map(HttpMethod::name)
                .collect(Collectors.joining(", "))
                : "N/A";

        String message = String.format(
                "HTTP method '%s' is not supported for this endpoint. Supported methods are: [%s]",
                ex.getMethod(),
                supportedMethods
        );

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(message, null));
    }

    // ─────────────────────────────────────────────
    // Database Errors
    // ─────────────────────────────────────────────

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvalidDataAccessApiUsage(
            InvalidDataAccessApiUsageException ex) {

        log.error("Database usage error: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid database request"));
    }

    // ─────────────────────────────────────────────
    // Illegal Argument
    // ─────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        log.error("Illegal argument error: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ─────────────────────────────────────────────
    // Catch-all
    // ─────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleGlobalException(
            Exception ex) {

        log.error("Unexpected error occurred", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "An unexpected error occurred. Please try again later."
                ));
    }
}