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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** E.164, e.g. {@code +2347068565534}. Required and unique for customers; null for admins. */
    @Column(name = "phone_number", unique = true, length = 14)
    private String phoneNumber;

    /** Delegating-encoder hash, e.g. {@code {bcrypt}$2a$10$...}. Never the raw password. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Hash of the 4-digit transaction PIN. Null for admins, who have no wallet. */
    @Column(name = "pin_hash", length = 100)
    private String pinHash;

    @Column(name = "failed_pin_attempts", nullable = false)
    private int failedPinAttempts;

    @Column(name = "pin_locked_until")
    private Instant pinLockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public User(String firstName, String lastName, String email, String phoneNumber,
                String passwordHash, String pinHash, Role role) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
        this.pinHash = pinHash;
        this.role = role;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    // ------------------------------------------------------------------ transaction PIN

    public boolean hasPin() {
        return pinHash != null;
    }

    public boolean isPinLocked(Instant now) {
        return pinLockedUntil != null && now.isBefore(pinLockedUntil);
    }

    /**
     * Counts a wrong PIN. Reaching {@code maxAttempts} locks the PIN for {@code lockDuration} and resets the
     * counter, so the customer gets a fresh set of attempts once the lock expires.
     *
     * @return attempts left before a lock, or 0 if this attempt locked the PIN
     */
    public int recordFailedPinAttempt(int maxAttempts, Duration lockDuration, Instant now) {
        failedPinAttempts++;
        if (failedPinAttempts >= maxAttempts) {
            failedPinAttempts = 0;
            pinLockedUntil = now.plus(lockDuration);
            return 0;
        }
        return maxAttempts - failedPinAttempts;
    }

    public void recordSuccessfulPinAttempt() {
        failedPinAttempts = 0;
        pinLockedUntil = null;
    }

    public void changePin(String newPinHash) {
        this.pinHash = newPinHash;
        recordSuccessfulPinAttempt();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
