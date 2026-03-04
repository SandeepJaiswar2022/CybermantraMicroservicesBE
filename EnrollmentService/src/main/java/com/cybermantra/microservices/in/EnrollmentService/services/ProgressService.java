package com.cybermantra.microservices.in.EnrollmentService.services;
import com.cybermantra.microservices.in.EnrollmentService.dto.request.ProgressUpdateRequest;
import com.cybermantra.microservices.in.EnrollmentService.dto.response.LectureProgressResponse;
import com.cybermantra.microservices.in.EnrollmentService.exceptions.AccessDeniedException;
import com.cybermantra.microservices.in.EnrollmentService.models.Enrollment;
import com.cybermantra.microservices.in.EnrollmentService.models.LectureProgress;
import com.cybermantra.microservices.in.EnrollmentService.repository.LectureProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressService {

    private final LectureProgressRepository lectureProgressRepository;
    private final EnrollmentService enrollmentService;

    // Total lectures per course — in real system, fetch from Course Service via Feign/REST
    // For now: injected or passed in request; here we use a placeholder approach
    private static final int DEFAULT_TOTAL_LECTURES = 5; // Replace with actual feign call

    @Transactional
    public LectureProgressResponse updateProgress(Long enrollmentId, UUID userId,
                                                  ProgressUpdateRequest request) {
        Enrollment enrollment = enrollmentService.findEnrollmentById(enrollmentId);
        verifyOwnership(enrollment, userId);

        LectureProgress progress = lectureProgressRepository
                .findByEnrollmentIdAndLectureId(enrollmentId, request.getLectureId())
                .orElse(LectureProgress.builder()
                        .enrollment(enrollment)
                        .lectureId(request.getLectureId())
                        .build());

        // Accumulate watch time
        progress.setWatchTimeSeconds(
                progress.getWatchTimeSeconds() + request.getWatchTimeSeconds());
        progress.setLastPositionSeconds(request.getLastPositionSeconds());

        if (Boolean.TRUE.equals(request.getMarkCompleted()) && !progress.getIsCompleted()) {
            progress.setIsCompleted(true);
            progress.setCompletedAt(LocalDateTime.now());
        }

        progress = lectureProgressRepository.save(progress);

        // Update enrollment's last accessed time and recalculate progress
        enrollment.setLastAccessedAt(LocalDateTime.now());
        recalculateCourseProgress(enrollment);

        return mapToResponse(progress);
    }

    @Transactional
    public LectureProgressResponse markLectureCompleted(Long enrollmentId, Long lectureId,
                                                        UUID userId) {
        Enrollment enrollment = enrollmentService.findEnrollmentById(enrollmentId);
        verifyOwnership(enrollment, userId);

        LectureProgress progress = lectureProgressRepository
                .findByEnrollmentIdAndLectureId(enrollmentId, lectureId)
                .orElse(LectureProgress.builder()
                        .enrollment(enrollment)
                        .lectureId(lectureId)
                        .build());

        if (!progress.getIsCompleted()) {
            progress.setIsCompleted(true);
            progress.setCompletedAt(LocalDateTime.now());
            progress = lectureProgressRepository.save(progress);

            enrollment.setLastAccessedAt(LocalDateTime.now());
            recalculateCourseProgress(enrollment);
        }

        return mapToResponse(progress);
    }

    @Transactional(readOnly = true)
    public List<LectureProgressResponse> getProgressForEnrollment(Long enrollmentId, UUID userId) {
        Enrollment enrollment = enrollmentService.findEnrollmentById(enrollmentId);
        verifyOwnership(enrollment, userId);

        return lectureProgressRepository.findAllByEnrollmentId(enrollmentId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void recalculateCourseProgress(Enrollment enrollment) {
        long completedCount = lectureProgressRepository
                .countByEnrollmentIdAndIsCompleted(enrollment.getId(), true);

        // TODO: Replace DEFAULT_TOTAL_LECTURES with actual count from Course Service
        int totalLectures = DEFAULT_TOTAL_LECTURES;

        if (totalLectures > 0) {
            BigDecimal percentage = BigDecimal.valueOf(completedCount)
                    .divide(BigDecimal.valueOf(totalLectures), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            enrollment.setProgressPercentage(percentage);

            if (percentage.compareTo(BigDecimal.valueOf(100)) >= 0) {
                enrollment.setIsCompleted(true);
                enrollment.setCompletionDate(LocalDateTime.now());
                log.info("Enrollment {} completed!", enrollment.getId());
            }
        }
    }

    private void verifyOwnership(Enrollment enrollment, UUID userId) {
        if (!enrollment.getUserId().equals(userId)) {
            throw new AccessDeniedException();
        }
    }

    private LectureProgressResponse mapToResponse(LectureProgress lp) {
        return LectureProgressResponse.builder()
                .id(lp.getId())
                .lectureId(lp.getLectureId())
                .isCompleted(lp.getIsCompleted())
                .watchTimeSeconds(lp.getWatchTimeSeconds())
                .lastPositionSeconds(lp.getLastPositionSeconds())
                .completedAt(lp.getCompletedAt())
                .build();
    }
}