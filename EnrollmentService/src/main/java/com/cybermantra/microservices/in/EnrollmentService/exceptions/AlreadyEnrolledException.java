package com.cybermantra.microservices.in.EnrollmentService.exceptions;

import java.util.UUID;

public class AlreadyEnrolledException extends RuntimeException {
    public AlreadyEnrolledException(UUID userId, Long courseId) {
        super("User " + userId + " is already enrolled in course " + courseId);
    }
}