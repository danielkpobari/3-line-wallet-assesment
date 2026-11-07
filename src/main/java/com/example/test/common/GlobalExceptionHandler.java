package com.example.test.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Expected rule violations: logged at INFO (they are client mistakes, not system faults). */
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.info("Request rejected: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return respond(ex.getErrorCode(), ex.getMessage(), request, Map.of());
    }

    /** {@code @Valid @RequestBody} failures. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return respond(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    /** Constraint failures on path variables, request params, headers (and bodies on the same method). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors()
                        .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error ->
                        fieldErrors.putIfAbsent(name != null ? name : "parameter", error.getDefaultMessage()));
            }
        });
        return respond(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    /** Query-string binding failures (e.g. an unknown enum value or a malformed date in search filters). */
    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiError> handleBind(BindException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(error -> fieldErrors.putIfAbsent(error.getField(),
                error.isBindingFailure() ? "has an invalid value" : error.getDefaultMessage()));
        return respond(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    /** Raised by method security (@PreAuthorize) inside the MVC layer, after the filter chain has passed. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied by method security on {}", request.getRequestURI());
        return respond(ErrorCode.FORBIDDEN, "You do not have permission to perform this action", request, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHORIZED, "A valid bearer token is required", request, Map.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                fieldErrors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return respond(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.info("Unreadable request body: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return respond(ErrorCode.MALFORMED_REQUEST, "Request body is missing or is not valid JSON", request, Map.of());
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> handleMissingInput(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.MALFORMED_REQUEST, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(ErrorCode.MALFORMED_REQUEST, "Invalid value for parameter '" + ex.getName() + "'",
                request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint matches this path", request, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                      HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ApiError> handleConcurrency(ConcurrencyFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent update rejected on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(ErrorCode.CONCURRENT_UPDATE, "The resource was modified concurrently; please retry",
                request, Map.of());
    }

    /** Last-resort guard for unique constraints hit by racing requests (e.g. same email or idempotency key). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.DATA_CONFLICT, "The request conflicts with existing data", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, Map.of());
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, String message, HttpServletRequest request,
                                             Map<String, String> fieldErrors) {
        if (code == ErrorCode.VALIDATION_ERROR && !fieldErrors.isEmpty()) {
            // Field names only: rejected values may contain personal data.
            log.info("Validation failed for fields {}", fieldErrors.keySet());
        }
        return ResponseEntity.status(code.status())
                .body(ApiError.of(code, message, request.getRequestURI(), fieldErrors));
    }
}
