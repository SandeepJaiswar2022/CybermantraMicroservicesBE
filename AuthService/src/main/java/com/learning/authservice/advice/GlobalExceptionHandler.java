package com.learning.authservice.advice;


import com.learning.authservice.dto.ApiResponse;
import com.learning.authservice.dto.ErrorResponse;
import com.learning.authservice.exception.AlreadyExistException;
import com.learning.authservice.exception.AuthException;
import com.learning.authservice.exception.ResourceNotFoundException;
import com.learning.authservice.exception.TokenRefreshException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
		return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.error(ex.getMessage()));
	}

	@ExceptionHandler(AlreadyExistException.class)
	public ResponseEntity<ApiResponse<Void>> handleAlreadyExistException(AlreadyExistException ex) {
		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(ApiResponse.error(ex.getMessage()));
	}

	@ExceptionHandler(TokenRefreshException.class)
	public ResponseEntity<ApiResponse<Void>> handleTokenRefreshException(TokenRefreshException ex) {
		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.error(ex.getMessage()));
	}
	@ExceptionHandler(AuthException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException ex) {
		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.error(ex.getMessage()));
	}
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<ErrorResponse>> handleGlobalException(
			Exception ex, WebRequest request) {

		log.error("Unexpected error: ", ex);

		ErrorResponse errorResponse = ErrorResponse.builder()
				.error("INTERNAL_SERVER_ERROR")
				.message("An unexpected error occurred")
				.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
				.path(request.getDescription(false).replace("uri=", ""))
				.timestamp(Instant.now().toEpochMilli())
				.build();

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.error("An unexpected error occurred"));
	}
}
