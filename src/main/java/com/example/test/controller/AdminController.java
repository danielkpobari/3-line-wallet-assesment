package com.example.test.controller;

import com.example.test.dto.AccountResponse;
import com.example.test.dto.AuditEventResponse;
import com.example.test.dto.PageResponse;
import com.example.test.dto.StatementEntryResponse;
import com.example.test.dto.TransactionResponse;
import com.example.test.dto.UpdateAccountStatusRequest;
import com.example.test.dto.search.AccountSearchCriteria;
import com.example.test.dto.search.AuditEventSearchCriteria;
import com.example.test.dto.search.StatementSearchCriteria;
import com.example.test.dto.search.TransactionSearchCriteria;
import com.example.test.service.AccountQueryService;
import com.example.test.service.AuditService;
import com.example.test.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Back-office endpoints. Protected by URL rule and, as defence in depth, by method security. */
@Tag(name = "Admin", description = "Back-office search, account management and audit trail (ADMIN role)")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AccountQueryService accountQueryService;
    private final WalletService walletService;
    private final AuditService auditService;

    @Operation(summary = "Search customers/accounts",
            description = "All filters optional, e.g. ?q=ada&status=ACTIVE&fromDate=2026-11-01")
    @GetMapping("/accounts")
    public PageResponse<AccountResponse> searchAccounts(
            @Valid @ParameterObject AccountSearchCriteria criteria,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_SIZE) @Min(1) @Max(ApiConstraints.MAX_PAGE_SIZE)
            int size) {
        return accountQueryService.searchAccounts(criteria, page, size);
    }

    @Operation(summary = "Get any account with owner details and balance")
    @GetMapping("/accounts/{accountNumber}")
    public AccountResponse getAccount(
            @PathVariable
            @Pattern(regexp = ApiConstraints.ACCOUNT_NUMBER_REGEX, message = ApiConstraints.ACCOUNT_NUMBER_MESSAGE)
            String accountNumber) {
        return accountQueryService.getAccount(accountNumber);
    }

    @Operation(summary = "Freeze, close or re-activate an account")
    @PatchMapping("/accounts/{accountNumber}/status")
    public AccountResponse changeStatus(
            @PathVariable
            @Pattern(regexp = ApiConstraints.ACCOUNT_NUMBER_REGEX, message = ApiConstraints.ACCOUNT_NUMBER_MESSAGE)
            String accountNumber,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return walletService.changeStatus(accountNumber, request.status());
    }

    @Operation(summary = "Search any account's statement")
    @GetMapping("/accounts/{accountNumber}/transactions")
    public PageResponse<StatementEntryResponse> searchStatement(
            @PathVariable
            @Pattern(regexp = ApiConstraints.ACCOUNT_NUMBER_REGEX, message = ApiConstraints.ACCOUNT_NUMBER_MESSAGE)
            String accountNumber,
            @Valid @ParameterObject StatementSearchCriteria criteria,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_SIZE) @Min(1) @Max(ApiConstraints.MAX_PAGE_SIZE)
            int size) {
        return accountQueryService.searchStatement(accountNumber, criteria, page, size);
    }

    @Operation(summary = "Search all transactions",
            description = "All filters optional, e.g. ?type=TRANSFER&accountNumber=1234567890&minAmount=5000")
    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> searchTransactions(
            @Valid @ParameterObject TransactionSearchCriteria criteria,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_SIZE) @Min(1) @Max(ApiConstraints.MAX_PAGE_SIZE)
            int size) {
        return accountQueryService.searchTransactions(criteria, page, size);
    }

    @Operation(summary = "Get any transaction by reference")
    @GetMapping("/transactions/{reference}")
    public TransactionResponse getTransaction(@PathVariable @Size(max = 40) String reference) {
        return accountQueryService.getTransaction(reference);
    }

    @Operation(summary = "Search the audit trail (newest first)",
            description = "All filters optional, e.g. ?actorUserId=12&action=TRANSFER_FAILED&fromDate=2026-11-01, "
                    + "or ?requestId=<X-Request-Id> to see everything one request did")
    @GetMapping("/audit-events")
    public PageResponse<AuditEventResponse> searchAuditEvents(
            @Valid @ParameterObject AuditEventSearchCriteria criteria,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_SIZE) @Min(1) @Max(ApiConstraints.MAX_PAGE_SIZE)
            int size) {
        return auditService.search(criteria, page, size);
    }
}
