package com.cybermantra.microservices.in.EnrollmentService.services;
import com.cybermantra.microservices.in.EnrollmentService.dto.request.EnrollmentRequest;
import com.cybermantra.microservices.in.EnrollmentService.dto.response.EnrollmentResponse;
import com.cybermantra.microservices.in.EnrollmentService.exceptions.AccessDeniedException;
import com.cybermantra.microservices.in.EnrollmentService.exceptions.AlreadyEnrolledException;
import com.cybermantra.microservices.in.EnrollmentService.exceptions.EnrollmentNotFoundException;
import com.cybermantra.microservices.in.EnrollmentService.models.Enrollment;
import com.cybermantra.microservices.in.EnrollmentService.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;

    @Transactional
    public EnrollmentResponse enroll(UUID userId, EnrollmentRequest request) {
        UUID targetUserId = request.getTargetUserId() != null ? request.getTargetUserId() : userId;

        if (enrollmentRepository.existsByUserIdAndCourseId(targetUserId, request.getCourseId())) {
            throw new AlreadyEnrolledException(targetUserId, request.getCourseId());
        }

        Enrollment enrollment = Enrollment.builder()
                .userId(targetUserId)
                .courseId(request.getCourseId())
                .build();

        enrollment = enrollmentRepository.save(enrollment);
        log.info("User {} enrolled in course {}", targetUserId, request.getCourseId());
        return mapToResponse(enrollment);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getUserEnrollments(UUID userId) {
        return enrollmentRepository.findAllByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse getEnrollment(Long enrollmentId, UUID requestingUserId) {
        Enrollment enrollment = findEnrollmentById(enrollmentId);
        verifyOwnership(enrollment, requestingUserId);
        return mapToResponse(enrollment);
    }

    @Transactional
    public void unenroll(Long enrollmentId, UUID requestingUserId) {
        Enrollment enrollment = findEnrollmentById(enrollmentId);
        verifyOwnership(enrollment, requestingUserId);
        enrollmentRepository.delete(enrollment);
        log.info("User {} unenrolled from enrollment {}", requestingUserId, enrollmentId);
    }

    // Called internally (e.g., after payment confirmation via event/feign)
    @Transactional
    public EnrollmentResponse createEnrollmentAfterPayment(UUID userId, Long courseId) {
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new AlreadyEnrolledException(userId, courseId);
        }
        Enrollment enrollment = Enrollment.builder()
                .userId(userId)
                .courseId(courseId)
                .build();
        return mapToResponse(enrollmentRepository.save(enrollment));
    }

    public Enrollment findEnrollmentById(Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
    }

    private void verifyOwnership(Enrollment enrollment, UUID userId) {
        if (!enrollment.getUserId().equals(userId)) {
            throw new AccessDeniedException();
        }
    }

    private EnrollmentResponse mapToResponse(Enrollment enrollment) {
        return EnrollmentResponse.builder()
                .id(enrollment.getId())
                .userId(enrollment.getUserId())
                .courseId(enrollment.getCourseId())
                .enrollmentDate(enrollment.getEnrollmentDate())
                .completionDate(enrollment.getCompletionDate())
                .isCompleted(enrollment.getIsCompleted())
                .progressPercentage(enrollment.getProgressPercentage())
                .lastAccessedAt(enrollment.getLastAccessedAt())
                .hasCertificate(enrollment.getCertificate() != null)
                .build();
    }
}