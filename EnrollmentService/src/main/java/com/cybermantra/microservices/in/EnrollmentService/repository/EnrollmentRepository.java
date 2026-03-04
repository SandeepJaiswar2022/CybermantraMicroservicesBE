package com.cybermantra.microservices.in.EnrollmentService.repository;

import com.cybermantra.microservices.in.EnrollmentService.models.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByUserIdAndCourseId(UUID userId, Long courseId);

    boolean existsByUserIdAndCourseId(UUID userId, Long courseId);

    List<Enrollment> findAllByUserId(UUID userId);

    List<Enrollment> findAllByUserIdAndIsCompleted(UUID userId, Boolean isCompleted);

    long countByUserIdAndIsCompleted(UUID userId, Boolean isCompleted);

    @Query("SELECT AVG(e.progressPercentage) FROM Enrollment e WHERE e.userId = :userId")
    Double findAverageCompletionRateByUserId(UUID userId);
}