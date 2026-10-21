package com.excercise.java25ai.exception;

/**
 * Raised when an enrollment is attempted on a course whose active enrollments have
 * reached its capacity. Maps to HTTP 409 / 400.
 */
public class CourseFullException extends RuntimeException {

    public CourseFullException(String message) {
        super(message);
    }

    public static CourseFullException of(String courseCode, int capacity) {
        return new CourseFullException(
                "Course '%s' has reached its capacity of %d active enrollments".formatted(courseCode, capacity));
    }
}
