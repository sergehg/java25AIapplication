package com.excercise.java25ai.exception;

/**
 * Raised when a requested aggregate does not exist. Maps to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resourceName, Object identifier) {
        return new ResourceNotFoundException("%s not found with identifier '%s'".formatted(resourceName, identifier));
    }
}
