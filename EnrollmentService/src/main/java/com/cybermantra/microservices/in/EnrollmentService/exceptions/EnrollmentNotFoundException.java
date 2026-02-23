package com.cybermantra.microservices.in.EnrollmentService.exceptions;

public class EnrollmentNotFoundException extends RuntimeException {
    public EnrollmentNotFoundException(Long id) {
        super("Enrollment not found with id: " + id);
    }
    public EnrollmentNotFoundException(String message) {
        super(message);
    }
}