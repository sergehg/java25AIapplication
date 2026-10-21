package com.excercise.java25ai.exception;

/**
 * Raised when a student already holds an active {@code ENROLLED} record for the same course.
 * Maps to HTTP 409 / 400.
 */
public class AlreadyEnrolledException extends RuntimeException {

    public AlreadyEnrolledException(String message) {
        super(message);
    }

    public static AlreadyEnrolledException of(Long studentId, String courseCode) {
        return new AlreadyEnrolledException(
                "Student %d is already actively enrolled in course '%s'".formatted(studentId, courseCode));
    }
}
