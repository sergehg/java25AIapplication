package com.excercise.java25ai.mapper;

import com.excercise.java25ai.dto.EnrollmentResponse;
import com.excercise.java25ai.entity.EnrollmentEntity;
import org.springframework.stereotype.Component;

/**
 * Translates {@link EnrollmentEntity} into its read model. Stateless, therefore thread-safe.
 *
 * <p>Both associations are dereferenced here, so callers must invoke this inside the
 * transaction that loaded the enrollment.
 */
@Component
public class EnrollmentMapper {

    public EnrollmentResponse toResponse(EnrollmentEntity enrollment) {
        var student = enrollment.getStudent();
        var course = enrollment.getCourse();
        return new EnrollmentResponse(
                enrollment.getId(),
                student.getId(),
                student.getFullName(),
                course.getId(),
                course.getCode(),
                course.getTitle(),
                enrollment.getEnrolledAt(),
                enrollment.getStatus(),
                enrollment.getClassroom()
        );
    }
}
