package com.example.test.service;

import com.example.test.common.BusinessException;
import com.example.test.common.ErrorCode;
import com.example.test.model.AuditAction;
import com.example.test.model.User;
import com.example.test.repo.UserRepo;
import com.example.test.security.CurrentUser;
import com.example.test.security.WalletSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Transaction PIN: creation rules, verification with lockout, and change.
 *
 * <p>PINs are stored as BCrypt hashes. After {@code wallet.security.pin.max-attempts} wrong PINs in a row the
 * PIN is locked for {@code wallet.security.pin.lock-duration}; a correct PIN resets the counter.
 */
@Service
@RequiredArgsConstructor
public class PinService {

    private static final String RESOURCE_USER = "USER";
    private static final DateTimeFormatter LOCK_TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.of("Africa/Lagos"));

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final WalletSecurityProperties properties;
    private final AuditService audit;

    /** Validates a new PIN against the strength rules and returns its hash. */
    public String hashNewPin(String pin) {
        requireStrong(pin);
        return passwordEncoder.encode(pin);
    }

    /**
     * Verifies the logged-in user's PIN before a sensitive action.
     *
     * <p>Runs in its own transaction ({@code REQUIRES_NEW}) and does not roll back on a wrong PIN, so the
     * failed-attempt count is saved even though the caller's transaction (e.g. the transfer) is rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessException.class)
    public void verify(String pin) {
        check(lockCurrentUser(), pin);
    }

    /**
     * Changes the logged-in user's PIN after verifying the current one (a wrong current PIN counts towards the
     * lockout).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void change(String currentPin, String newPin) {
        // Validate the new PIN first so a weak choice does not cost the customer an attempt.
        requireStrong(newPin);
        if (newPin.equals(currentPin)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "New PIN must be different from the current PIN");
        }
        User user = lockCurrentUser();
        check(user, currentPin);
        user.changePin(passwordEncoder.encode(newPin));
        audit.event(AuditAction.PIN_CHANGED).actor(user.getId(), user.getRole())
                .resource(RESOURCE_USER, idOf(user)).recordSuccess();
    }

    // ------------------------------------------------------------------ helpers

    private User lockCurrentUser() {
        return userRepo.findByIdForUpdate(CurrentUser.get().id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "User no longer exists"));
    }

    private void check(User user, String pin) {
        if (!user.hasPin()) {
            throw new BusinessException(ErrorCode.PIN_NOT_SET, "No transaction PIN is set for this user");
        }
        Instant now = Instant.now();
        if (user.isPinLocked(now)) {
            audit.event(AuditAction.PIN_VERIFICATION_FAILED).actor(user.getId(), user.getRole())
                    .resource(RESOURCE_USER, idOf(user)).detail("lockedUntil", user.getPinLockedUntil())
                    .recordFailure(ErrorCode.PIN_LOCKED.name());
            throw locked(user.getPinLockedUntil());
        }
        if (pin == null || !passwordEncoder.matches(pin, user.getPinHash())) {
            WalletSecurityProperties.Pin policy = properties.pin();
            int remaining = user.recordFailedPinAttempt(policy.maxAttempts(), policy.lockDuration(), now);
            // Recorded in this transaction, which commits despite the wrong PIN (noRollbackFor on the callers).
            if (remaining == 0) {
                audit.event(AuditAction.PIN_LOCKED).actor(user.getId(), user.getRole())
                        .resource(RESOURCE_USER, idOf(user)).detail("lockedUntil", user.getPinLockedUntil())
                        .recordFailure(ErrorCode.PIN_LOCKED.name());
                throw locked(user.getPinLockedUntil());
            }
            audit.event(AuditAction.PIN_VERIFICATION_FAILED).actor(user.getId(), user.getRole())
                    .resource(RESOURCE_USER, idOf(user)).detail("attemptsRemaining", remaining)
                    .recordFailure(ErrorCode.INVALID_PIN.name());
            throw new BusinessException(ErrorCode.INVALID_PIN,
                    "Incorrect PIN. " + remaining + " attempt(s) remaining before your PIN is locked");
        }
        user.recordSuccessfulPinAttempt();
    }

    private static String idOf(User user) {
        return String.valueOf(user.getId());
    }

    private static BusinessException locked(Instant until) {
        return new BusinessException(ErrorCode.PIN_LOCKED,
                "Too many incorrect PIN attempts. Try again after " + LOCK_TIME.format(until));
    }

    /** Exactly 4 digits, not all the same (0000) and not a straight run (1234, 9876). */
    static void requireStrong(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PIN must be exactly 4 digits");
        }
        boolean allSame = pin.chars().distinct().count() == 1;
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < pin.length(); i++) {
            int step = pin.charAt(i) - pin.charAt(i - 1);
            ascending &= step == 1;
            descending &= step == -1;
        }
        if (allSame || ascending || descending) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "PIN is too easy to guess; avoid repeated digits and sequences like 1234");
        }
    }
}
