package com.excercise.java25ai.service;

import com.excercise.java25ai.dto.EnrollStudentRequest;
import com.excercise.java25ai.dto.EnrollmentResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Enrollment operations and the rules that govern them.
 */
public interface EnrollmentService {

    /**
     * Enrolls an {@code ACTIVE} student in an {@code OPEN} course.
     *
     * <p>Rejects a student who is already actively enrolled, and a course whose active
     * enrollments have reached capacity. A previously {@code DROPPED} enrollment is reactivated
     * in place rather than duplicated. The course closes automatically once the last seat goes.
     */
    EnrollmentResponse enroll(@Valid EnrollStudentRequest request);

    /**
     * Marks an active enrollment {@code DROPPED}, freeing a seat and reopening the course if it
     * had closed.
     */
    EnrollmentResponse drop(Long enrollmentId);

    /**
     * Marks an active enrollment {@code COMPLETED}, freeing a seat and reopening the course if
     * it had closed.
     */
    EnrollmentResponse complete(Long enrollmentId);

    EnrollmentResponse getById(Long enrollmentId);

    Page<EnrollmentResponse> findByStudent(Long studentId, Pageable pageable);

    Page<EnrollmentResponse> findByCourse(Long courseId, Pageable pageable);
}
