package com.kalima.backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Unified error response body for all API endpoints. The shape is stable and
 * documented — clients may parse {@link #code()} as the machine-readable
 * discriminator and show {@link #message()} to the user.
 *
 * <p>Null collections / fields are omitted from the JSON output to keep small
 * payloads for simple errors.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldIssue> errors
) {

    public static ApiError of(int status, String error, String code,
                              String message, String path) {
        return new ApiError(Instant.now(), status, error, code, message, path, null);
    }

    public static ApiError of(int status, String error, String code,
                              String message, String path, List<FieldIssue> errors) {
        return new ApiError(Instant.now(), status, error, code, message, path, errors);
    }

    public record FieldIssue(String field, String message) {
    }
}
