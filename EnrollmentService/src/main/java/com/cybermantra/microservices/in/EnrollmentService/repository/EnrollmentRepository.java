package com.cybermantra.microservices.in.EnrollmentService.repository;

import com.cybermantra.microservices.in.EnrollmentService.models.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByUserIdAndCourseId(Long userId, Long courseId);

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    List<Enrollment> findAllByUserId(Long userId);

    List<Enrollment> findAllByUserIdAndIsCompleted(Long userId, Boolean isCompleted);

    long countByUserIdAndIsCompleted(Long userId, Boolean isCompleted);

    @Query("SELECT AVG(e.progressPercentage) FROM Enrollment e WHERE e.userId = :userId")
    Double findAverageCompletionRateByUserId(Long userId);
}