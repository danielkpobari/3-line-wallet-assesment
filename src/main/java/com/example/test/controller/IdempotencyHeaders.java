package com.example.test.controller;

import com.example.test.dto.TransactionResponse;
import com.example.test.service.TransactionResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class IdempotencyHeaders {

    static final String KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private IdempotencyHeaders() {
    }

    /** New transactions get {@code createdStatus}; replays get 200 with {@code Idempotent-Replayed: true}. */
    static ResponseEntity<TransactionResponse> toResponse(TransactionResult result, HttpStatus createdStatus) {
        TransactionResponse body = result.transaction();
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED, "true").body(body);
        }
        return ResponseEntity.status(createdStatus).body(body);
    }
}
