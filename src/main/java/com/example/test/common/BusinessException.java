package com.example.test.common;

/** A rule violation the client can act on. Mapped to an HTTP response by {@link GlobalExceptionHandler}. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
