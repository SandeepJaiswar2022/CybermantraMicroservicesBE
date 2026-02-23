package com.cybermantra.microservices.in.EnrollmentService.repository;

import com.cybermantra.microservices.in.EnrollmentService.models.CourseNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseNoteRepository extends JpaRepository<CourseNote, Long> {

    List<CourseNote> findAllByEnrollmentId(Long enrollmentId);

    List<CourseNote> findAllByEnrollmentIdAndLectureId(Long enrollmentId, Long lectureId);
}