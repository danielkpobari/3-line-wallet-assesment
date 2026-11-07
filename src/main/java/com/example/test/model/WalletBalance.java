package com.example.test.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Current balance of a wallet. Balances only change through {@link #credit} and {@link #debit}
 * so that the non-negative invariant is enforced in one place (and again by a DB check constraint).
 */
@Entity
@Table(name = "wallet_balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Optimistic version; a second line of defence on top of the pessimistic row lock used for transfers. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WalletBalance empty() {
        WalletBalance balance = new WalletBalance();
        balance.amount = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        return balance;
    }

    public void credit(BigDecimal value) {
        requirePositive(value);
        amount = amount.add(value);
    }

    public void debit(BigDecimal value) {
        requirePositive(value);
        if (amount.compareTo(value) < 0) {
            throw new IllegalStateException("Debit would make wallet balance negative");
        }
        amount = amount.subtract(value);
    }

    public boolean canDebit(BigDecimal value) {
        return amount.compareTo(value) >= 0;
    }

    private static void requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
