package com.example.test.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import org.hibernate.annotations.Immutable;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable record of one balance movement on one account. A transfer produces exactly one DEBIT
 * on the source and one CREDIT on the destination, so debits and credits for a transfer always net to zero.
 */
@Entity
@Immutable
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private WalletTransaction transaction;

    @Column(name = "account_number", nullable = false, length = 10, updatable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private LedgerEntry(WalletTransaction transaction, String accountNumber, EntryType entryType,
                        BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter) {
        this.transaction = transaction;
        this.accountNumber = accountNumber;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
    }

    public static LedgerEntry debit(WalletTransaction transaction, String accountNumber, BigDecimal amount,
                                    BigDecimal balanceBefore, BigDecimal balanceAfter) {
        return new LedgerEntry(transaction, accountNumber, EntryType.DEBIT, amount, balanceBefore, balanceAfter);
    }

    public static LedgerEntry credit(WalletTransaction transaction, String accountNumber, BigDecimal amount,
                                     BigDecimal balanceBefore, BigDecimal balanceAfter) {
        return new LedgerEntry(transaction, accountNumber, EntryType.CREDIT, amount, balanceBefore, balanceAfter);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
