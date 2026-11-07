package com.example.test.dto;

import com.example.test.model.Account;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceResponse(String accountNumber, String currency, BigDecimal balance, Instant asOf) {

    public static BalanceResponse from(Account account) {
        return new BalanceResponse(account.getAccountNumber(), account.getCurrency(),
                account.getWalletBalance().getAmount(), Instant.now());
    }
}
