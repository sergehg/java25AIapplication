package com.excercise.java25ai.dto;

import com.excercise.java25ai.entity.enums.CourseStatus;

/**
 * Read model for a course, including live occupancy so clients can render availability
 * without a second call.
 */
public record CourseResponse(
        Long id,
        String code,
        String title,
        String description,
        Integer capacity,
        long activeEnrollments,
        long availableSeats,
        CourseStatus status
) {
}
