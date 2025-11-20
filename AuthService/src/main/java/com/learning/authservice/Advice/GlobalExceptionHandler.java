package com.learning.authservice.Advice;


import com.learning.authservice.dto.ApiResponse;
import com.learning.authservice.exception.AlreadyExistException;
import com.learning.authservice.exception.ResourceNotFoundException;
import com.learning.authservice.exception.TokenRefreshException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
		return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(ex.getMessage()));
	}

	@ExceptionHandler(AlreadyExistException.class)
	public ResponseEntity<ApiResponse<Void>> handleAlreadyExistException(AlreadyExistException ex) {
		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(ApiResponse.failure(ex.getMessage()));
	}

	@ExceptionHandler(TokenRefreshException.class)
	public ResponseEntity<ApiResponse<Void>> handleTokenRefreshException(TokenRefreshException ex) {
		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.failure(ex.getMessage()));
	}
}
