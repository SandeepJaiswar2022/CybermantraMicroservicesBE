package com.lms.content.exception;

import com.lms.content.dto.response.ApiResponse;
import com.lms.content.util.CorrelationIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * Centralized exception handler.
 *
 * <p>All exceptions map to a consistent {@link ApiResponse} envelope so
 * frontend and API clients always get the same response shape regardless of error type.
 *
 * <p>We log at ERROR level with the correlationId so errors are traceable
 * across microservices in distributed tracing tools (e.g., Zipkin, Jaeger).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ContentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ContentNotFoundException ex) {
        log.warn("[{}] Content not found: {}", CorrelationIdUtil.get(), ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("CONTENT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(OwnershipException.class)
    public ResponseEntity<ApiResponse<Void>> handleOwnership(OwnershipException ex) {
        log.warn("[{}] Ownership violation: {}", CorrelationIdUtil.get(), ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("OWNERSHIP_MISMATCH", ex.getMessage()));
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFile(InvalidFileException ex) {
        log.warn("[{}] Invalid file: {}", CorrelationIdUtil.get(), ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiResponse.error("INVALID_FILE", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("[{}] File too large: {}", CorrelationIdUtil.get(), ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ApiResponse.error("FILE_TOO_LARGE", "Uploaded file exceeds the maximum allowed size."));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorage(StorageException ex) {
        log.error("[{}] Storage failure: {}", CorrelationIdUtil.get(), ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error("STORAGE_FAILURE", "Storage operation failed. Please try again later."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("[{}] Access denied: {}", CorrelationIdUtil.get(), ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("ACCESS_DENIED", "You do not have permission to perform this action."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        log.warn("[{}] Validation failed: {}", CorrelationIdUtil.get(), errors);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION_ERROR", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("[{}] Unexpected error: {}", CorrelationIdUtil.get(), ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred."));
    }
}
