package com.learning.authservice.advice;


import com.learning.authservice.dto.ApiResponse;
import com.learning.authservice.dto.ErrorResponse;
import com.learning.authservice.exception.AlreadyExistException;
import com.learning.authservice.exception.AuthException;
import com.learning.authservice.exception.ResourceNotFoundException;
import com.learning.authservice.exception.TokenRefreshException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
			Exception ex) {

		log.error("Unexpected error: ", ex);

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.error("An unexpected error occurred"));
	}
}
