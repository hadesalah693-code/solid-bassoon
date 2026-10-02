package com.kalima.backend.common.error;

/**
 * Machine-readable error codes exposed in {@link ApiError#code()}. Treat this
 * as part of the public API — renaming values is a breaking change for
 * clients. New codes can be added freely.
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    // 400
    public static final String VALIDATION_FAILED     = "VALIDATION_FAILED";
    public static final String MALFORMED_JSON        = "MALFORMED_JSON";
    public static final String TYPE_MISMATCH         = "TYPE_MISMATCH";
    public static final String MISSING_PARAMETER     = "MISSING_PARAMETER";
    public static final String BAD_REQUEST           = "BAD_REQUEST";

    // 401 / 403
    public static final String UNAUTHORIZED          = "UNAUTHORIZED";
    public static final String ACCESS_DENIED         = "ACCESS_DENIED";

    // 429
    public static final String TOO_MANY_REQUESTS     = "TOO_MANY_REQUESTS";

    // 404
    public static final String NOT_FOUND             = "NOT_FOUND";
    public static final String ROUTE_NOT_FOUND       = "ROUTE_NOT_FOUND";

    // 405
    public static final String METHOD_NOT_ALLOWED    = "METHOD_NOT_ALLOWED";

    // 409
    public static final String CONFLICT              = "CONFLICT";
    public static final String CONSTRAINT_VIOLATION  = "CONSTRAINT_VIOLATION";

    // 5xx
    public static final String INTERNAL_ERROR        = "INTERNAL_ERROR";
}
