package com.excercise.java25ai.dto;

import com.excercise.java25ai.entity.enums.Classroom;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for enrolling a student in a course.
 */
public record EnrollStudentRequest(

        @NotNull(message = "Student id is required")
        Long studentId,

        @NotNull(message = "Course id is required")
        Long courseId,

        @NotNull(message = "Classroom is required")
        Classroom classroom
) {
}
