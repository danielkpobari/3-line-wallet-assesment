package com.example.test.dto;

import com.example.test.model.Account;
import com.example.test.model.Beneficiary;

import java.time.Instant;

public record BeneficiaryResponse(
        Long id,
        String accountNumber,
        String phoneAccountNumber,
        String accountName,
        String nickname,
        Instant createdAt) {

    public static BeneficiaryResponse from(Beneficiary beneficiary) {
        Account account = beneficiary.getAccount();
        return new BeneficiaryResponse(
                beneficiary.getId(),
                account.getAccountNumber(),
                account.getPhoneAccountNumber(),
                account.getUser().getFullName(),
                beneficiary.getNickname(),
                beneficiary.getCreatedAt());
    }
}
