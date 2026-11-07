package com.example.test.service;

import com.example.test.repo.AccountRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates random 10-digit account numbers whose first digit is 1-6.
 *
 * <p>Never 0, so the number survives clients treating it as numeric; never 7-9, because those are reserved for
 * phone account numbers (Nigerian mobile numbers without the leading 0), so the two kinds can never collide.
 * Uniqueness is checked here and guaranteed by DB unique constraints.
 */
@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

    static final int MAX_ATTEMPTS = 20;
    private static final long LOWEST = 1_000_000_000L;
    private static final long HIGHEST_EXCLUSIVE = 7_000_000_000L;

    private final AccountRepo accountRepo;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = Long.toString(random.nextLong(LOWEST, HIGHEST_EXCLUSIVE));
            if (!accountRepo.existsByAccountNumberOrPhoneAccountNumber(candidate, candidate)) {
                return candidate;
            }
        }
        // Practically unreachable with 6 billion candidates; surfaces as a 500 and is logged.
        throw new IllegalStateException(
                "Could not generate a unique account number after " + MAX_ATTEMPTS + " attempts");
    }
}
