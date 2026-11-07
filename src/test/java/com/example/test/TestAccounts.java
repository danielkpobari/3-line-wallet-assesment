package com.example.test;

import com.example.test.dto.AccountResponse;
import com.example.test.dto.RegisterRequest;
import com.example.test.service.WalletService;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Test helper: registers a customer with a unique email. */
public final class TestAccounts {

    public static final String PASSWORD = "Passw0rd!";
    public static final String PIN = "2580";

    private TestAccounts() {
    }

    public static AccountResponse create(WalletService walletService) {
        return walletService.register(request("user-" + UUID.randomUUID() + "@example.com"));
    }

    public static RegisterRequest request(String email) {
        return new RegisterRequest("Test", "User", email, randomPhone(), PASSWORD, PIN);
    }

    /** A random, valid Nigerian mobile number in local format, e.g. 08031234567. */
    public static String randomPhone() {
        String[] prefixes = {"070", "080", "081", "090", "091"};
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return prefixes[random.nextInt(prefixes.length)] + String.format("%08d", random.nextInt(100_000_000));
    }
}
