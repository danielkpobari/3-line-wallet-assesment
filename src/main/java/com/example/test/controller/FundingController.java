package com.example.test.controller;

import com.example.test.dto.FundWalletRequest;
import com.example.test.dto.TransactionResponse;
import com.example.test.service.TransactionResult;
import com.example.test.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-only top-up so transfers can be exercised. Admin-only, and registered only when
 * {@code wallet.funding.enabled=true}; a real system credits wallets from verified payment or bank events.
 */
@Tag(name = "Admin", description = "Back-office search and account management (ADMIN role)")
@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wallet.funding", name = "enabled", havingValue = "true")
public class FundingController {

    private final WalletService walletService;

    @Operation(summary = "Credit a wallet (simulated funding, demo only)")
    @PostMapping("/{accountNumber}/fund")
    public ResponseEntity<TransactionResponse> fund(
            @PathVariable
            @Pattern(regexp = ApiConstraints.ACCOUNT_NUMBER_REGEX, message = ApiConstraints.ACCOUNT_NUMBER_MESSAGE)
            String accountNumber,
            @Parameter(description = "Client-generated unique key (e.g. a UUID), max 64 chars")
            @RequestHeader(name = IdempotencyHeaders.KEY, required = false) @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody FundWalletRequest request) {
        TransactionResult result = walletService.fund(accountNumber, request.amount(), request.narration(),
                idempotencyKey);
        return IdempotencyHeaders.toResponse(result, HttpStatus.CREATED);
    }
}
