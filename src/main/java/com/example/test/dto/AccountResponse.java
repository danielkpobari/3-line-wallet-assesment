package com.example.test.dto;

import com.example.test.model.Account;
import com.example.test.model.AccountStatus;
import com.example.test.model.User;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code accountNumber} and {@code phoneAccountNumber} both receive into the same wallet. */
public record AccountResponse(
        Long userId,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String accountNumber,
        String phoneAccountNumber,
        String currency,
        AccountStatus status,
        BigDecimal balance,
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        User user = account.getUser();
        return new AccountResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                account.getAccountNumber(),
                account.getPhoneAccountNumber(),
                account.getCurrency(),
                account.getStatus(),
                account.getWalletBalance().getAmount(),
                account.getCreatedAt());
    }
}
