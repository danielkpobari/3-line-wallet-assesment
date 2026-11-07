package com.example.test.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A customer's account. Associations are LAZY; repositories fetch what each use case needs with entity graphs
 * or fetch joins, so there are no hidden per-row selects.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Generated 10-digit account number (first digit 1-6). Ledger and transactions always use this one. */
    @Column(name = "account_number", nullable = false, unique = true, length = 10)
    private String accountNumber;

    /** The owner's mobile number without the leading 0 (first digit 7-9); an alias that receives into this account. */
    @Column(name = "phone_account_number", nullable = false, unique = true, length = 10)
    private String phoneAccountNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "wallet_balance_id", nullable = false, unique = true)
    private WalletBalance walletBalance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Account(String accountNumber, String phoneAccountNumber, User user, WalletBalance walletBalance,
                   String currency) {
        this.accountNumber = accountNumber;
        this.phoneAccountNumber = phoneAccountNumber;
        this.user = user;
        this.walletBalance = walletBalance;
        this.currency = currency;
        this.status = AccountStatus.ACTIVE;
    }

    public void changeStatus(AccountStatus newStatus) {
        this.status = newStatus;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
