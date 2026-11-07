package com.example.test.service;

import com.example.test.TestAccounts;
import com.example.test.common.BusinessException;
import com.example.test.common.ErrorCode;
import com.example.test.dto.AccountResponse;
import com.example.test.dto.DoTransDto;
import com.example.test.dto.RegisterRequest;
import com.example.test.dto.TransferRequest;
import com.example.test.model.EntryType;
import com.example.test.model.LedgerEntry;
import com.example.test.model.Role;
import com.example.test.model.User;
import com.example.test.model.WalletTransaction;
import com.example.test.repo.AccountRepo;
import com.example.test.repo.LedgerEntryRepo;
import com.example.test.repo.UserRepo;
import com.example.test.repo.WalletTransactionRepo;
import com.example.test.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class WalletServiceIntegrationTest {

    @Autowired
    WalletService walletService;
    @Autowired
    AccountRepo accountRepo;
    @Autowired
    UserRepo userRepo;
    @Autowired
    WalletTransactionRepo transactionRepo;
    @Autowired
    LedgerEntryRepo ledgerEntryRepo;

    @Test
    void registersUserWithTenDigitAccountAndZeroBalanceWallet() {
        AccountResponse account = TestAccounts.create(walletService);

        assertThat(account.userId()).isNotNull();
        assertThat(account.accountNumber()).matches("[1-6]\\d{9}");
        assertThat(account.phoneAccountNumber()).matches("[789][01]\\d{8}");
        assertThat(account.phoneNumber()).isEqualTo("+234" + account.phoneAccountNumber());
        assertThat(account.currency()).isEqualTo("NGN");
        assertThat(account.status().name()).isEqualTo("ACTIVE");
        assertThat(account.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void normalisesEmailAndRejectsDuplicatesCaseInsensitively() {
        String email = "Mixed.Case-" + UUID.randomUUID() + "@Example.com";
        AccountResponse account = walletService.register(TestAccounts.request("  " + email + " "));

        assertThat(account.email()).isEqualTo(email.toLowerCase());
        assertBusinessError(ErrorCode.DUPLICATE_EMAIL,
                () -> walletService.register(TestAccounts.request(email.toUpperCase())));
    }

    @Test
    void storesOnlyHashesOfThePasswordAndPin() {
        AccountResponse account = TestAccounts.create(walletService);
        User user = userRepo.findById(account.userId()).orElseThrow();

        assertThat(user.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(TestAccounts.PASSWORD);
        assertThat(user.getPinHash()).startsWith("{bcrypt}").doesNotContain(TestAccounts.PIN);
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void transferMovesFundsAndWritesBalancedLedgerEntries() {
        AccountResponse sender = TestAccounts.create(walletService);
        AccountResponse receiver = TestAccounts.create(walletService);
        fund(sender, "500.00");

        TransactionResult result = walletService.transfer(
                new DoTransDto(sender.accountNumber(), receiver.accountNumber(), new BigDecimal("125.50"), "Lunch"),
                null);

        assertThat(result.replayed()).isFalse();
        assertThat(result.transaction().reference()).startsWith("TRF");
        assertThat(result.transaction().narration()).isEqualTo("Lunch");
        assertThat(balanceOf(sender)).isEqualByComparingTo("374.50");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("125.50");

        List<LedgerEntry> entries = entriesOf(result);
        assertThat(entries).hasSize(2);
        LedgerEntry debit = entries.get(0);
        LedgerEntry credit = entries.get(1);
        assertThat(debit.getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(debit.getAccountNumber()).isEqualTo(sender.accountNumber());
        assertThat(debit.getBalanceBefore()).isEqualByComparingTo("500.00");
        assertThat(debit.getBalanceAfter()).isEqualByComparingTo("374.50");
        assertThat(credit.getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(credit.getAccountNumber()).isEqualTo(receiver.accountNumber());
        assertThat(credit.getBalanceBefore()).isEqualByComparingTo("0.00");
        assertThat(credit.getBalanceAfter()).isEqualByComparingTo("125.50");
    }

    @Test
    void loggedInUserWithoutAWalletCannotTransfer() {
        AccountResponse receiver = TestAccounts.create(walletService);
        logIn(new CurrentUser(Long.MAX_VALUE, "admin@example.com", Role.ADMIN));

        assertBusinessError(ErrorCode.ACCOUNT_NOT_FOUND, () -> walletService.transfer(
                new TransferRequest(receiver.accountNumber(), BigDecimal.ONE, null, TestAccounts.PIN, null), null));
    }

    @Test
    void customerTransferRequiresALoggedInUser() {
        AccountResponse receiver = TestAccounts.create(walletService);

        assertThatThrownBy(() -> walletService.transfer(
                new TransferRequest(receiver.accountNumber(), BigDecimal.ONE, null, TestAccounts.PIN, null), null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void transferToPhoneAccountNumberCreditsTheSameWalletAndRecordsTheCanonicalNumber() {
        AccountResponse sender = TestAccounts.create(walletService);
        AccountResponse receiver = TestAccounts.create(walletService);
        fund(sender, "100.00");

        TransactionResult result = walletService.transfer(
                new DoTransDto(sender.accountNumber(), receiver.phoneAccountNumber(), new BigDecimal("30.00")), null);

        assertThat(result.transaction().toAccount()).isEqualTo(receiver.accountNumber());
        assertThat(balanceOf(receiver)).isEqualByComparingTo("30.00");
    }

    @Test
    void transferToOwnPhoneAccountNumberIsASameAccountTransfer() {
        AccountResponse account = TestAccounts.create(walletService);
        fund(account, "10.00");

        assertBusinessError(ErrorCode.SAME_ACCOUNT_TRANSFER, () -> walletService.transfer(
                new DoTransDto(account.accountNumber(), account.phoneAccountNumber(), BigDecimal.ONE), null));
    }

    @Test
    void rejectsDuplicatePhoneNumberInEitherFormat() {
        String local = TestAccounts.randomPhone();
        walletService.register(new RegisterRequest("A", "B",
                "a-" + UUID.randomUUID() + "@example.com", local, TestAccounts.PASSWORD, TestAccounts.PIN));

        String international = "+234" + local.substring(1);
        assertBusinessError(ErrorCode.DUPLICATE_PHONE, () -> walletService.register(
                new RegisterRequest("C", "D", "c-" + UUID.randomUUID() + "@example.com",
                        international, TestAccounts.PASSWORD, TestAccounts.PIN)));
    }

    @Test
    void allowsTransferOfEntireBalance() {
        AccountResponse sender = TestAccounts.create(walletService);
        AccountResponse receiver = TestAccounts.create(walletService);
        fund(sender, "80.00");

        walletService.transfer(new DoTransDto(sender.accountNumber(), receiver.accountNumber(),
                new BigDecimal("80")), null);

        assertThat(balanceOf(sender)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("80.00");
    }

    @Test
    void rejectsInsufficientFundsWithoutChangingBalances() {
        AccountResponse sender = TestAccounts.create(walletService);
        AccountResponse receiver = TestAccounts.create(walletService);
        fund(sender, "10.00");

        assertBusinessError(ErrorCode.INSUFFICIENT_FUNDS, () -> walletService.transfer(
                new DoTransDto(sender.accountNumber(), receiver.accountNumber(), new BigDecimal("10.01")), null));

        assertThat(balanceOf(sender)).isEqualByComparingTo("10.00");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsTransferToSameAccount() {
        AccountResponse account = TestAccounts.create(walletService);

        assertBusinessError(ErrorCode.SAME_ACCOUNT_TRANSFER, () -> walletService.transfer(
                new DoTransDto(account.accountNumber(), account.accountNumber(), BigDecimal.ONE), null));
    }

    @Test
    void rejectsUnknownDestinationAccount() {
        AccountResponse sender = TestAccounts.create(walletService);
        fund(sender, "10.00");

        assertBusinessError(ErrorCode.ACCOUNT_NOT_FOUND, () -> walletService.transfer(
                new DoTransDto(sender.accountNumber(), "0000000000", BigDecimal.ONE), null));
    }

    @Test
    void rejectsZeroNegativeAndSubKoboAmounts() {
        String from = TestAccounts.create(walletService).accountNumber();
        String to = TestAccounts.create(walletService).accountNumber();

        assertBusinessError(ErrorCode.INVALID_AMOUNT,
                () -> walletService.transfer(new DoTransDto(from, to, BigDecimal.ZERO), null));
        assertBusinessError(ErrorCode.INVALID_AMOUNT,
                () -> walletService.transfer(new DoTransDto(from, to, new BigDecimal("-5.00")), null));
        assertBusinessError(ErrorCode.INVALID_AMOUNT,
                () -> walletService.transfer(new DoTransDto(from, to, new BigDecimal("1.005")), null));
    }

    @Test
    void repeatedTransferWithSameIdempotencyKeyMovesMoneyOnce() {
        AccountResponse sender = TestAccounts.create(walletService);
        AccountResponse receiver = TestAccounts.create(walletService);
        fund(sender, "100.00");
        DoTransDto request = new DoTransDto(sender.accountNumber(), receiver.accountNumber(), new BigDecimal("40.00"));
        String key = UUID.randomUUID().toString();

        TransactionResult first = walletService.transfer(request, key);
        TransactionResult second = walletService.transfer(request, key);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.transaction().reference()).isEqualTo(first.transaction().reference());
        assertThat(balanceOf(sender)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("40.00");
    }

    @Test
    void rejectsIdempotencyKeyReusedForDifferentRequest() {
        AccountResponse sender = TestAccounts.create(walletService);
        AccountResponse receiver = TestAccounts.create(walletService);
        fund(sender, "100.00");
        String key = UUID.randomUUID().toString();
        walletService.transfer(new DoTransDto(sender.accountNumber(), receiver.accountNumber(),
                new BigDecimal("10.00")), key);

        assertBusinessError(ErrorCode.IDEMPOTENCY_KEY_REUSED, () -> walletService.transfer(
                new DoTransDto(sender.accountNumber(), receiver.accountNumber(), new BigDecimal("11.00")), key));
        assertThat(balanceOf(sender)).isEqualByComparingTo("90.00");
    }

    @Test
    void fundingCreditsWalletAndWritesSingleCreditEntry() {
        AccountResponse account = TestAccounts.create(walletService);

        TransactionResult result = walletService.fund(account.accountNumber(), new BigDecimal("250.75"),
                "Top-up", null);

        assertThat(balanceOf(account)).isEqualByComparingTo("250.75");
        assertThat(result.transaction().reference()).startsWith("FND");
        assertThat(result.transaction().fromAccount()).isNull();
        assertThat(entriesOf(result)).singleElement().satisfies(entry -> {
            assertThat(entry.getEntryType()).isEqualTo(EntryType.CREDIT);
            assertThat(entry.getBalanceAfter()).isEqualByComparingTo("250.75");
        });
    }

    @AfterEach
    void logOut() {
        SecurityContextHolder.clearContext();
    }

    private static void logIn(CurrentUser user) {
        SecurityContextHolder.getContext().setAuthentication(user);
    }

    private void fund(AccountResponse account, String amount) {
        walletService.fund(account.accountNumber(), new BigDecimal(amount), null, null);
    }

    private BigDecimal balanceOf(AccountResponse account) {
        return accountRepo.findByAccountNumber(account.accountNumber()).orElseThrow()
                .getWalletBalance().getAmount();
    }

    private List<LedgerEntry> entriesOf(TransactionResult result) {
        WalletTransaction tx = transactionRepo.findByReference(result.transaction().reference()).orElseThrow();
        return ledgerEntryRepo.findByTransactionOrderByIdAsc(tx);
    }

    private static void assertBusinessError(ErrorCode expected, ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
