package com.mobisec.in.courseservice.repository.specification;

import com.mobisec.in.courseservice.entity.Course;
import com.mobisec.in.courseservice.enums.CourseLevel;
import com.mobisec.in.courseservice.enums.CourseStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class CourseSpecifications {

    public static Specification<Course> isNotDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("isDeleted"));
    }

    public static Specification<Course> isPublished() {
        return (root, query, cb) -> cb.isTrue(root.get("isPublished"));
    }

    public static Specification<Course> byCategoryId(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Course> bySubcategoryId(UUID subcategoryId) {
        return (root, query, cb) -> cb.equal(root.get("subcategory").get("id"), subcategoryId);
    }

    public static Specification<Course> byLevel(CourseLevel level) {
        return (root, query, cb) -> cb.equal(root.get("level"), level);
    }

    public static Specification<Course> byInstructorId(UUID instructorId) {
        return (root, query, cb) -> cb.equal(root.get("instructorId"), instructorId);
    }

    public static Specification<Course> byStatus(CourseStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Course> byPublishedStatus(Boolean isPublished) {
        return (root, query, cb) -> cb.equal(root.get("isPublished"), isPublished);
    }

    public static Specification<Course> searchByTitleOrDescription(String searchTerm) {
        return (root, query, cb) -> {
            String pattern = "%" + searchTerm.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }
}