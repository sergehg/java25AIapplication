package com.excercise.java25ai.dto;

import com.excercise.java25ai.entity.enums.StudentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read model for a student. {@code age} and {@code fullName} are derived at mapping time so
 * clients never have to compute them.
 */
public record StudentResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        LocalDate dateOfBirth,
        int age,
        StudentStatus status,
        LocalDateTime createdAt
) {
}
