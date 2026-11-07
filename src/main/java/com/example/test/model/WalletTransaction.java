package com.example.test.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import org.hibernate.annotations.Immutable;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** One business transaction (a transfer or a funding). Its balance effects live in {@link LedgerEntry}. */
@Entity
@Immutable
@Table(name = "wallet_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    /** Null for funding, which has no source wallet. */
    @Column(name = "from_account", length = 10)
    private String fromAccount;

    @Column(name = "to_account", nullable = false, length = 10)
    private String toAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 100)
    private String narration;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private WalletTransaction(String reference, String idempotencyKey, TransactionType type,
                              String fromAccount, String toAccount, BigDecimal amount,
                              String currency, String narration) {
        this.reference = reference;
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.status = TransactionStatus.SUCCESSFUL;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.currency = currency;
        this.narration = narration;
    }

    public static WalletTransaction transfer(String reference, String idempotencyKey, String fromAccount,
                                             String toAccount, BigDecimal amount, String currency,
                                             String narration) {
        return new WalletTransaction(reference, idempotencyKey, TransactionType.TRANSFER,
                fromAccount, toAccount, amount, currency, narration);
    }

    public static WalletTransaction funding(String reference, String idempotencyKey, String toAccount,
                                            BigDecimal amount, String currency, String narration) {
        return new WalletTransaction(reference, idempotencyKey, TransactionType.FUNDING,
                null, toAccount, amount, currency, narration);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
