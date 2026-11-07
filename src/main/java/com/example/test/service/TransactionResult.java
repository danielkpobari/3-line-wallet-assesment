package com.example.test.service;

import com.example.test.dto.TransactionResponse;

/**
 * Outcome of a money-moving call.
 *
 * @param transaction the resulting transaction
 * @param replayed    true when an earlier transaction with the same idempotency key was returned
 *                    instead of moving money again
 */
public record TransactionResult(TransactionResponse transaction, boolean replayed) {
}
