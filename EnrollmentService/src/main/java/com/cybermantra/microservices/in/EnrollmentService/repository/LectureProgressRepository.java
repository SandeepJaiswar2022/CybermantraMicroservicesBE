package com.cybermantra.microservices.in.EnrollmentService.repository;

import com.cybermantra.microservices.in.EnrollmentService.models.LectureProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LectureProgressRepository extends JpaRepository<LectureProgress, Long> {

    Optional<LectureProgress> findByEnrollmentIdAndLectureId(Long enrollmentId, Long lectureId);

    List<LectureProgress> findAllByEnrollmentId(Long enrollmentId);

    long countByEnrollmentIdAndIsCompleted(Long enrollmentId, Boolean isCompleted);
}