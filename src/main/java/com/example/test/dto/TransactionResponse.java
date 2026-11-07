package com.example.test.dto;

import com.example.test.model.TransactionStatus;
import com.example.test.model.TransactionType;
import com.example.test.model.WalletTransaction;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String reference,
        TransactionType type,
        TransactionStatus status,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String currency,
        String narration,
        Instant createdAt) {

    public static TransactionResponse from(WalletTransaction tx) {
        return new TransactionResponse(
                tx.getReference(),
                tx.getType(),
                tx.getStatus(),
                tx.getFromAccount(),
                tx.getToAccount(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getNarration(),
                tx.getCreatedAt());
    }
}
