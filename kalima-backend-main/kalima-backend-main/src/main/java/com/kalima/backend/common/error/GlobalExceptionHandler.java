package com.kalima.backend.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Central translator for all unhandled exceptions thrown by controllers.
 * Produces a consistent {@link ApiError} payload so the frontend can rely on
 * a single shape.
 *
 * <p>Behavior:
 * <ul>
 *   <li>4xx are logged at DEBUG — they are client errors and normal.</li>
 *   <li>5xx are logged at ERROR with the full stack trace.</li>
 *   <li>Never leaks stack traces, SQL fragments or rejected values
 *       (especially passwords) to the client.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- Validation ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest req) {
        List<ApiError.FieldIssue> issues = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldIssue(fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Request validation failed", req, issues);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                               HttpServletRequest req) {
        List<ApiError.FieldIssue> issues = ex.getConstraintViolations().stream()
                .map(this::toFieldIssue)
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Request validation failed", req, issues);
    }

    private ApiError.FieldIssue toFieldIssue(ConstraintViolation<?> v) {
        String path = v.getPropertyPath() != null ? v.getPropertyPath().toString() : "";
        // Paths look like "methodName.argName.field" — keep the last segment.
        int dot = path.lastIndexOf('.');
        String field = dot >= 0 ? path.substring(dot + 1) : path;
        return new ApiError.FieldIssue(field, v.getMessage());
    }

    // ---------- Bad input ----------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                         HttpServletRequest req) {
        // The underlying Jackson message can be very long; keep it short and
        // safe — never echo user payload back.
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_JSON,
                "Malformed JSON request body", req, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "value";
        String message = "Parameter '" + ex.getName() + "' must be a valid " + expected;
        return build(HttpStatus.BAD_REQUEST, ErrorCode.TYPE_MISMATCH, message, req, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest req) {
        String message = "Missing required parameter: " + ex.getParameterName();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MISSING_PARAMETER, message, req, null);
    }

    // ---------- Status-carrying exceptions ----------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex,
                                                         HttpServletRequest req) {
        HttpStatusCode statusCode = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return build(status, codeForStatus(status), message, req, null);
    }

    // ---------- Security ----------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                "Access denied", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex,
                                               HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                "Authentication required", req, null);
    }

    // ---------- Persistence ----------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest req) {
        // Never leak the constraint name or SQL. Log at WARN so ops can debug.
        log.warn("Data integrity violation on {} {}: {}",
                req.getMethod(), req.getRequestURI(), rootMessage(ex));
        return build(HttpStatus.CONFLICT, ErrorCode.CONSTRAINT_VIOLATION,
                "Operation conflicts with an existing record", req, null);
    }

    // ---------- Routing ----------

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex,
                                                    HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.ROUTE_NOT_FOUND,
                "No endpoint for " + ex.getHttpMethod() + " " + ex.getRequestURL(),
                req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                            HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                "Method " + ex.getMethod() + " not allowed for this endpoint", req, null);
    }

    // ---------- Fallback ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred", req, null);
    }

    // ---------- Helpers ----------

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest req,
                                           List<ApiError.FieldIssue> issues) {
        if (log.isDebugEnabled()) {
            log.debug("API error: {} {} -> {} ({}): {}",
                    req.getMethod(), req.getRequestURI(),
                    status.value(), code, message);
        }
        ApiError body = ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                req.getRequestURI(),
                issues
        );
        return ResponseEntity.status(status).body(body);
    }

    private String codeForStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST       -> ErrorCode.BAD_REQUEST;
            case UNAUTHORIZED      -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN         -> ErrorCode.ACCESS_DENIED;
            case NOT_FOUND         -> ErrorCode.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case CONFLICT          -> ErrorCode.CONFLICT;
            case TOO_MANY_REQUESTS -> ErrorCode.TOO_MANY_REQUESTS;
            default                -> status.is5xxServerError()
                    ? ErrorCode.INTERNAL_ERROR
                    : ErrorCode.BAD_REQUEST;
        };
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage();
    }
}
