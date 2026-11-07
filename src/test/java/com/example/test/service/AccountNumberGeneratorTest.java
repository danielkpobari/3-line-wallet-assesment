package com.example.test.service;

import com.example.test.repo.AccountRepo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountNumberGeneratorTest {

    private final AccountRepo accountRepo = mock(AccountRepo.class);
    private final AccountNumberGenerator generator = new AccountNumberGenerator(accountRepo);

    @Test
    void generatesTenDigitNumberStartingWithOneToSixSoItNeverLooksLikeAPhoneNumber() {
        when(accountRepo.existsByAccountNumberOrPhoneAccountNumber(anyString(), anyString())).thenReturn(false);

        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.generate()).matches("[1-6]\\d{9}");
        }
    }

    @Test
    void retriesWhenCandidateAlreadyExists() {
        when(accountRepo.existsByAccountNumberOrPhoneAccountNumber(anyString(), anyString())).thenReturn(true, true, false);

        assertThat(generator.generate()).hasSize(10);
        verify(accountRepo, times(3)).existsByAccountNumberOrPhoneAccountNumber(anyString(), anyString());
    }

    @Test
    void failsAfterMaxAttempts() {
        when(accountRepo.existsByAccountNumberOrPhoneAccountNumber(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
        verify(accountRepo, times(AccountNumberGenerator.MAX_ATTEMPTS)).existsByAccountNumberOrPhoneAccountNumber(anyString(), anyString());
    }
}
