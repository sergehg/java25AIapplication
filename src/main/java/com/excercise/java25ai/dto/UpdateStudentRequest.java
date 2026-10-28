package com.excercise.java25ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for modifying a student's basic details. Status changes go through the
 * dedicated lifecycle operations, not through this payload.
 */
public record UpdateStudentRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must not exceed 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must not exceed 50 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a well-formed address")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email
) {
}
