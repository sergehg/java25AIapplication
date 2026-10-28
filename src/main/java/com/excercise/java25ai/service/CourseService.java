package com.excercise.java25ai.service;

import com.excercise.java25ai.dto.CourseResponse;
import com.excercise.java25ai.dto.CreateCourseRequest;
import com.excercise.java25ai.dto.UpdateCourseRequest;
import com.excercise.java25ai.entity.enums.CourseStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Course catalogue and lifecycle operations.
 */
public interface CourseService {

    CourseResponse create(@Valid CreateCourseRequest request);

    CourseResponse getById(Long id);

    /**
     * Course codes are matched case-insensitively, so {@code JAVA-25} and {@code java-25}
     * resolve to the same course.
     */
    CourseResponse getByCode(String code);

    /**
     * @param status optional filter; {@code null} returns courses of every status
     */
    Page<CourseResponse> findAll(CourseStatus status, Pageable pageable);

    /**
     * Every course currently accepting enrollments.
     */
    List<CourseResponse> findOpenCourses();

    /**
     * Updates title, description and capacity. Capacity may not drop below the number of
     * students currently enrolled, and the course status is re-evaluated afterwards.
     */
    CourseResponse update(Long id, @Valid UpdateCourseRequest request);

    /**
     * Soft delete: marks the course {@code ARCHIVED}. Rejected while active enrollments remain.
     */
    CourseResponse archive(Long id);

    /**
     * Hard delete. Rejected when the course has any enrollment record, active or historical.
     */
    void delete(Long id);
}
