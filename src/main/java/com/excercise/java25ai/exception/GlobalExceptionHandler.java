package com.excercise.java25ai.exception;

import com.excercise.java25ai.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Translates every exception escaping the controllers into the uniform {@link ErrorResponse}
 * payload. Nothing else in the application writes an error body.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String FALLBACK_MESSAGE =
            "An unexpected error occurred. Please contact support if the problem persists.";

    // --- 400: request could not be validated ---

    /**
     * {@code @Valid} failure on a request body.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestBody(MethodArgumentNotValidException exception,
                                                                 HttpServletRequest request) {
        var fieldErrors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.merge(error.getField(), messageOf(error), GlobalExceptionHandler::join));
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> fieldErrors.merge(error.getObjectName(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"),
                        GlobalExceptionHandler::join));

        return build(HttpStatus.BAD_REQUEST,
                "Validation failed for %d field(s)".formatted(fieldErrors.size()), request, fieldErrors);
    }

    /**
     * Constraint failure on a path variable, request parameter or other handler argument.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestParameter(HandlerMethodValidationException exception,
                                                                      HttpServletRequest request) {
        var fieldErrors = new LinkedHashMap<String, String>();
        for (var result : exception.getParameterValidationResults()) {
            var parameterName = Objects.requireNonNullElse(
                    result.getMethodParameter().getParameterName(),
                    "parameter" + result.getMethodParameter().getParameterIndex());
            result.getResolvableErrors().forEach(error -> fieldErrors.merge(parameterName,
                    Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"),
                    GlobalExceptionHandler::join));
        }
        return build(HttpStatus.BAD_REQUEST,
                "Validation failed for %d parameter(s)".formatted(fieldErrors.size()), request, fieldErrors);
    }

    /**
     * Constraint failure raised by a {@code @Validated} service bean, which is the safety net
     * behind the controller-level {@code @Valid}.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
                                                                  HttpServletRequest request) {
        var fieldErrors = new LinkedHashMap<String, String>();
        exception.getConstraintViolations()
                .forEach(violation -> fieldErrors.merge(propertyNameOf(violation), violation.getMessage(),
                        GlobalExceptionHandler::join));
        return build(HttpStatus.BAD_REQUEST,
                "Validation failed for %d field(s)".formatted(fieldErrors.size()), request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                              HttpServletRequest request) {
        // The raw parser message can echo back payload fragments, so it is logged, not returned.
        log.debug("Unreadable request body on {}", request.getRequestURI(), exception);
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                            HttpServletRequest request) {
        var requiredType = exception.getRequiredType();
        var message = "Parameter '%s' has an invalid value%s".formatted(
                exception.getName(),
                requiredType == null ? "" : "; expected type " + requiredType.getSimpleName());
        return build(HttpStatus.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception,
                                                                HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Required parameter '%s' is missing".formatted(exception.getParameterName()), request, null);
    }

    /**
     * Business rules with no dedicated exception: blocked deletions, capacity reduction below the
     * active enrollment count, enrolling an inactive student, illegal status transitions.
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleViolation(BusinessRuleViolationException exception,
                                                                     HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request, null);
    }

    // --- 404 ---

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception,
                                                        HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
    }

    // --- 409: the payload is well formed but conflicts with current state ---

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException exception,
                                                          HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(AlreadyEnrolledException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyEnrolled(AlreadyEnrolledException exception,
                                                               HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(CourseFullException.class)
    public ResponseEntity<ErrorResponse> handleCourseFull(CourseFullException exception,
                                                          HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    /**
     * Database-level backstop. A unique constraint tripping here means two concurrent requests
     * raced past the service check, so the message stays generic rather than leaking SQL.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception,
                                                                      HttpServletRequest request) {
        log.warn("Data integrity violation on {}", request.getRequestURI(), exception);
        return build(HttpStatus.CONFLICT,
                "The request conflicts with existing data and could not be completed", request, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockFailure(ObjectOptimisticLockingFailureException exception,
                                                                      HttpServletRequest request) {
        log.warn("Optimistic locking failure on {}", request.getRequestURI(), exception);
        return build(HttpStatus.CONFLICT,
                "The resource was modified concurrently; please retry the request", request, null);
    }

    // --- 500 ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        // The real cause is logged in full; the client only ever sees the safe fallback.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, FALLBACK_MESSAGE, request, null);
    }

    // --- helpers ---

    private ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                String message,
                                                HttpServletRequest request,
                                                Map<String, String> fieldErrors) {
        var body = fieldErrors == null || fieldErrors.isEmpty()
                ? ErrorResponse.of(status, message, request.getRequestURI())
                : ErrorResponse.of(status, message, request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    private static String messageOf(FieldError error) {
        return Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid");
    }

    /**
     * Keeps every message when one field trips several constraints.
     */
    private static String join(String existing, String addition) {
        return existing + "; " + addition;
    }

    /**
     * Reduces a violation path such as {@code create.request.email} to just {@code email}, so the
     * response never exposes service method or parameter names.
     */
    private static String propertyNameOf(ConstraintViolation<?> violation) {
        var path = violation.getPropertyPath().toString();
        var lastSeparator = path.lastIndexOf('.');
        return lastSeparator < 0 ? path : path.substring(lastSeparator + 1);
    }
}
