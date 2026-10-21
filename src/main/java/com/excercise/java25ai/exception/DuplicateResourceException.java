package com.excercise.java25ai.exception;

/**
 * Raised when a uniqueness invariant is violated, such as a duplicate student email
 * or a duplicate course code. Maps to HTTP 409 / 400.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public static DuplicateResourceException of(String resourceName, String field, Object value) {
        return new DuplicateResourceException(
                "%s already exists with %s '%s'".formatted(resourceName, field, value));
    }
}
