package com.example.test.common;

import org.springframework.http.HttpStatus;

/** Stable, machine-readable error codes returned in {@link ApiError#code()}. */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    INVALID_PIN(HttpStatus.FORBIDDEN),
    PIN_LOCKED(HttpStatus.LOCKED),

    EMAIL_NOT_REGISTERED(HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    BENEFICIARY_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT),
    DUPLICATE_PHONE(HttpStatus.CONFLICT),
    BENEFICIARY_EXISTS(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),
    DATA_CONFLICT(HttpStatus.CONFLICT),

    SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY),
    PIN_NOT_SET(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_BENEFICIARY(HttpStatus.UNPROCESSABLE_ENTITY),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
