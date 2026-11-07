package com.example.test.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** Error body returned for every failed request. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(ErrorCode code, String message, String path, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), code.status().value(), code.status().getReasonPhrase(),
                code.name(), message, path, fieldErrors);
    }
}
