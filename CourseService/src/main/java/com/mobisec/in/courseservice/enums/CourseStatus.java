package com.mobisec.in.courseservice.enums;

public enum CourseStatus {
    DRAFT,          // Instructor is working on it
    PENDING_REVIEW, // Submitted for admin review
    APPROVED,       // Approved by admin (ready to publish)
    PUBLISHED,      // (Optional)
    REJECTED,       // Admin rejected
    UNPUBLISHED     // Was published, now taken down
}
