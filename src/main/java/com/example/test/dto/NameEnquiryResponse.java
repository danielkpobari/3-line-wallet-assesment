package com.example.test.dto;

import com.example.test.model.Account;

/** Confirms who owns an account before sending money, without exposing contact details or balance. */
public record NameEnquiryResponse(String accountNumber, String accountName) {

    public static NameEnquiryResponse from(Account account) {
        return new NameEnquiryResponse(account.getAccountNumber(), account.getUser().getFullName());
    }
}
