package com.excercise.java25ai.exception;

/**
 * Raised when an operation is rejected by a business rule that has no dedicated exception of
 * its own: deleting a student or course that still has enrollments, reducing a course capacity
 * below its active enrollment count, enrolling an inactive student or a non-open course, and
 * illegal enrollment status transitions. Maps to HTTP 400.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
