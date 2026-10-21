package com.excercise.java25ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The single error payload shape returned by every failed request, as mandated by the business
 * rules: {@code timestamp}, {@code status}, {@code error}, {@code message}, {@code path}.
 *
 * <p>{@code fieldErrors} is the one optional member. It carries per-field detail for validation
 * failures and is omitted from the JSON entirely for every other kind of error, so the contract
 * stays uniform.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, path, null);
    }

    public static ErrorResponse of(HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(
                LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
    }
}
