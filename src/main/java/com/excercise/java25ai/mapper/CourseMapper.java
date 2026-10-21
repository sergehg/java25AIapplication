package com.excercise.java25ai.mapper;

import com.excercise.java25ai.dto.CourseResponse;
import com.excercise.java25ai.dto.CreateCourseRequest;
import com.excercise.java25ai.entity.CourseEntity;
import com.excercise.java25ai.entity.enums.CourseStatus;
import org.springframework.stereotype.Component;

/**
 * Translates between {@link CourseEntity} and its DTOs. Stateless, therefore thread-safe.
 */
@Component
public class CourseMapper {

    /**
     * Builds a new entity from a creation payload. The initial status is fixed to
     * {@code OPEN} here rather than taken from the caller.
     */
    public CourseEntity toEntity(CreateCourseRequest request) {
        return CourseEntity.builder()
                .code(request.code())
                .title(request.title())
                .description(request.description())
                .capacity(request.capacity())
                .status(CourseStatus.OPEN)
                .build();
    }

    /**
     * @param activeEnrollments current {@code ENROLLED} count, supplied by the service so the
     *                          mapper never has to touch the lazy enrollments collection.
     */
    public CourseResponse toResponse(CourseEntity course, long activeEnrollments) {
        long availableSeats = Math.max(0L, course.getCapacity() - activeEnrollments);
        return new CourseResponse(
                course.getId(),
                course.getCode(),
                course.getTitle(),
                course.getDescription(),
                course.getCapacity(),
                activeEnrollments,
                availableSeats,
                course.getStatus()
        );
    }
}
