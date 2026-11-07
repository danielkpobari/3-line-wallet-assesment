package com.example.test.service;

import com.example.test.common.BusinessException;
import com.example.test.common.ErrorCode;
import com.example.test.dto.AccountResponse;
import com.example.test.dto.BalanceResponse;
import com.example.test.dto.NameEnquiryResponse;
import com.example.test.dto.PageResponse;
import com.example.test.dto.StatementEntryResponse;
import com.example.test.dto.TransactionResponse;
import com.example.test.dto.search.AccountSearchCriteria;
import com.example.test.dto.search.StatementSearchCriteria;
import com.example.test.dto.search.TransactionSearchCriteria;
import com.example.test.model.Account;
import com.example.test.model.WalletTransaction;
import com.example.test.repo.AccountRepo;
import com.example.test.repo.LedgerEntryRepo;
import com.example.test.repo.WalletTransactionRepo;
import com.example.test.repo.spec.AccountSpecifications;
import com.example.test.repo.spec.LedgerEntrySpecifications;
import com.example.test.repo.spec.TransactionSpecifications;
import com.example.test.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the wallet. All methods return DTOs so no lazy entity escapes the transaction.
 * "My" methods are scoped to the logged-in user ({@link CurrentUser#get()}); the others are for administrators.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "id");

    private final AccountRepo accountRepo;
    private final WalletTransactionRepo transactionRepo;
    private final LedgerEntryRepo ledgerEntryRepo;

    // ------------------------------------------------------------------ current user

    public AccountResponse getMyAccount() {
        return AccountResponse.from(findMyAccount());
    }

    public BalanceResponse getMyBalance() {
        return BalanceResponse.from(findMyAccount());
    }

    public PageResponse<StatementEntryResponse> searchMyStatement(StatementSearchCriteria criteria, int page, int size) {
        String myAccount = accountRepo.findAccountNumberByUserId(CurrentUser.get().id()).orElseThrow(AccountQueryService::noWallet);
        return statement(myAccount, criteria, page, size);
    }

    /**
     * Returns a transaction only if the logged-in user is a party to it, or is an admin. Otherwise responds as
     * "not found" so the endpoint cannot be used to discover other customers' references.
     */
    public TransactionResponse getTransaction(String reference) {
        CurrentUser currentUser = CurrentUser.get();
        WalletTransaction tx = findTransaction(reference);
        if (!currentUser.isAdmin()) {
            String myAccount = accountRepo.findAccountNumberByUserId(currentUser.id()).orElse(null);
            boolean isParty = myAccount != null && (myAccount.equals(tx.getFromAccount()) || myAccount.equals(tx.getToAccount()));
            if (!isParty) {
                throw transactionNotFound(reference);
            }
        }
        return TransactionResponse.from(tx);
    }

    /**
     * Name enquiry before a transfer, by account number or phone account number. Returns only the canonical
     * account number and the holder's name, never contact details or balance.
     */
    public NameEnquiryResponse nameEnquiry(String number) {
        return NameEnquiryResponse.from(findAccount(number));
    }

    // ------------------------------------------------------------------ administration

    public PageResponse<AccountResponse> searchAccounts(AccountSearchCriteria criteria, int page, int size) {
        return PageResponse.from(accountRepo.findAll(AccountSpecifications.matching(criteria), pageOf(page, size)), AccountResponse::from);
    }

    public AccountResponse getAccount(String accountNumber) {
        return AccountResponse.from(findAccount(accountNumber));
    }

    public PageResponse<StatementEntryResponse> searchStatement(String number, StatementSearchCriteria criteria, int page, int size) {
        String accountNumber = accountRepo.resolveAccountNumber(number).orElseThrow(() -> accountNotFound(number));
        return statement(accountNumber, criteria, page, size);
    }

    public PageResponse<TransactionResponse> searchTransactions(TransactionSearchCriteria criteria, int page, int size) {
        // Transactions store canonical account numbers, so a phone account number filter is translated first.
        TransactionSearchCriteria effective = criteria;
        if (criteria.accountNumber() != null && !criteria.accountNumber().isBlank()) {
            effective = criteria.withAccountNumber(accountRepo.resolveAccountNumber(criteria.accountNumber()).orElse(criteria.accountNumber()));
        }
        return PageResponse.from(transactionRepo.findAll(TransactionSpecifications.matching(effective), pageOf(page, size)), TransactionResponse::from);
    }

    // ------------------------------------------------------------------ helpers

    private PageResponse<StatementEntryResponse> statement(String accountNumber, StatementSearchCriteria criteria, int page, int size) {
        StatementSearchCriteria filters = criteria != null ? criteria : StatementSearchCriteria.none();
        return PageResponse.from(ledgerEntryRepo.findAll(LedgerEntrySpecifications.forAccount(accountNumber).and(LedgerEntrySpecifications.matching(filters)), pageOf(page, size)), StatementEntryResponse::from);
    }

    private static Pageable pageOf(int page, int size) {
        return PageRequest.of(page, size, NEWEST_FIRST);
    }

    private Account findMyAccount() {
        return accountRepo.findByUserId(CurrentUser.get().id()).orElseThrow(AccountQueryService::noWallet);
    }

    private static BusinessException noWallet() {
        return new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "You do not have a wallet");
    }

    /** By account number or phone account number. */
    private Account findAccount(String number) {
        return accountRepo.findByAnyNumber(number).orElseThrow(() -> accountNotFound(number));
    }

    private WalletTransaction findTransaction(String reference) {
        return transactionRepo.findByReference(reference).orElseThrow(() -> transactionNotFound(reference));
    }

    private static BusinessException accountNotFound(String accountNumber) {
        return new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Account " + accountNumber + " was not found");
    }

    private static BusinessException transactionNotFound(String reference) {
        return new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction " + reference + " was not found");
    }
}
