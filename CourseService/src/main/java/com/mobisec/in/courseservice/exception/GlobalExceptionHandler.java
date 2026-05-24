package com.mobisec.in.courseservice.exception;


import com.mobisec.in.courseservice.dto.common.ApiResponse;
import com.mobisec.in.courseservice.dto.common.ErrorResponse;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {

        log.error("Resource not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleDuplicateResourceException(
            DuplicateResourceException ex) {

        log.error("Duplicate resource: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvalidOperationException(
            InvalidOperationException ex) {

        log.error("Invalid operation: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvalidInputException(
            InvalidInputException ex) {

        log.error("Invalid Input: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleUnauthorizedException(
            UnauthorizedException ex) {

        log.error("Unauthorized access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleForbiddenException(
            ForbiddenException ex) {

        log.error("Forbidden access: {}", ex.getMessage());


        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

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


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String paramName = ex.getName();
        Object rawValue = ex.getValue();
        String invalidValue = stringifyInvalidValue(rawValue);
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

        } else if (requiredType == Boolean.class || requiredType == boolean.class) {

            message = String.format(
                    "Invalid value '%s' for parameter '%s'. Allowed values are: true or false",
                    invalidValue, paramName
            );

        } else if (requiredType == UUID.class) {

            message = String.format(
                    "Invalid UUID '%s' for parameter '%s'. Expected a valid UUID format",
                    invalidValue, paramName
            );

        } else if (requiredType != null && (Number.class.isAssignableFrom(requiredType)
                || requiredType.isPrimitive())) {

            message = String.format(
                    "Invalid value '%s' for parameter '%s'. Expected a numeric value",
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {

        String rootMessage = Optional.of(ex.getMostSpecificCause())
                .map(Throwable::getMessage)
                .orElse("Request body is missing or malformed");

        log.error("Request body error: {}", rootMessage);

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Request body is required and must be valid requested JSON"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {

        log.error("Unsupported media type: {}", ex.getContentType());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Content-Type must be application/json"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {

        String message = "Invalid API endpoint or malformed URL. Please check the request path and query parameters.";
        log.error("No resource found: {}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(message, null));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleGlobalException(
            Exception ex) {

        log.error("Unexpected error: ", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }

    /**
     * Handle JWT authentication exceptions
     */
    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwtAuthenticationException(
            JwtAuthenticationException ex) {

        log.error("JWT Authentication failed: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        String message = ex.getMessage();

        // Handle pagination specific errors
        log.error("Illegal argument error: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvalidDataAccessApiUsage(
            InvalidDataAccessApiUsageException ex) {

        String message = ex.getMessage();

        // Friendly pagination-specific message
        if (message != null && message.contains("Page offset exceeds Integer.MAX_VALUE")) {
            message = "Requested page is too large. Please use a smaller page index or size.";
        }

        log.error("Invalid data access usage: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message));
    }

    /**
     * Handle Spring Security access denied exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex) {

        log.error("Forbidden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You don't have permission to access this resource"));
    }

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


    private String stringifyInvalidValue(Object value) {
        if (value == null) return "null";

        if (value.getClass().isArray()) {
            if (value instanceof Object[] objArr) {
                return Arrays.stream(objArr)
                        .map(String::valueOf)
                        .collect(Collectors.joining(", "));
            }
        }
        return String.valueOf(value);
    }
}