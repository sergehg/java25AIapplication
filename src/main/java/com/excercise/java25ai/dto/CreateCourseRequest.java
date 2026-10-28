package com.excercise.java25ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for defining a new course offering. Status is never accepted from the caller:
 * a new course always starts as {@code OPEN}.
 */
public record CreateCourseRequest(

        @NotBlank(message = "Course code is required")
        @Size(max = 20, message = "Course code must not exceed 20 characters")
        String code,

        @NotBlank(message = "Title is required")
        @Size(max = 100, message = "Title must not exceed 100 characters")
        String title,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 500, message = "Capacity must not exceed 500")
        Integer capacity
) {
}
