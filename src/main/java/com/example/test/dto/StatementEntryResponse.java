package com.example.test.dto;

import com.example.test.model.EntryType;
import com.example.test.model.LedgerEntry;
import com.example.test.model.TransactionType;
import com.example.test.model.WalletTransaction;

import java.math.BigDecimal;
import java.time.Instant;

/** One line of an account statement, seen from that account's point of view. */
public record StatementEntryResponse(
        String reference,
        TransactionType transactionType,
        EntryType entryType,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String counterpartyAccount,
        String narration,
        Instant createdAt) {

    public static StatementEntryResponse from(LedgerEntry entry) {
        WalletTransaction tx = entry.getTransaction();
        String counterparty = entry.getEntryType() == EntryType.DEBIT ? tx.getToAccount() : tx.getFromAccount();
        return new StatementEntryResponse(
                tx.getReference(),
                tx.getType(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getBalanceBefore(),
                entry.getBalanceAfter(),
                counterparty,
                tx.getNarration(),
                entry.getCreatedAt());
    }
}
