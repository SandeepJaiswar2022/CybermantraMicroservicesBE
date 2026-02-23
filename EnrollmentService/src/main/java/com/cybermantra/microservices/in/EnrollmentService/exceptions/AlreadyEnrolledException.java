package com.cybermantra.microservices.in.EnrollmentService.exceptions;

public class AlreadyEnrolledException extends RuntimeException {
    public AlreadyEnrolledException(Long userId, Long courseId) {
        super("User " + userId + " is already enrolled in course " + courseId);
    }
}