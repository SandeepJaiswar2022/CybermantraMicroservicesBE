package com.cybermantra.microservices.in.EnrollmentService.exceptions;

public class CourseNotCompletedException extends RuntimeException {
    public CourseNotCompletedException(Long enrollmentId) {
        super("Course not completed for enrollment: " + enrollmentId + ". Complete all lectures to earn a certificate.");
    }
}