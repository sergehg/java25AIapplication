package com.excercise.java25ai.dto;

import com.excercise.java25ai.entity.enums.Classroom;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;

import java.time.LocalDateTime;

/**
 * Read model for an enrollment. Both sides of the relationship are flattened into scalar
 * fields so serializing a response can never walk back into a lazy association.
 */
public record EnrollmentResponse(
        Long id,
        Long studentId,
        String studentName,
        Long courseId,
        String courseCode,
        String courseTitle,
        LocalDateTime enrolledAt,
        EnrollmentStatus status,
        Classroom classroom
) {
}
