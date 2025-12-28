package com.mobisec.in.courseservice.repository;

import com.mobisec.in.courseservice.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID>,
        JpaSpecificationExecutor<Course> {

    Optional<Course> findByIdAndIsDeletedFalse(UUID id);

    List<Course> findByInstructorIdAndIsDeletedFalse(UUID instructorId);

    Page<Course> findByIsPublishedTrueAndIsDeletedFalse(Pageable pageable);
}