package com.example.test.controller;

import com.example.test.common.ApiError;
import com.example.test.dto.BeneficiaryTransferRequest;
import com.example.test.dto.TransactionResponse;
import com.example.test.dto.TransferRequest;
import com.example.test.service.TransactionResult;
import com.example.test.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transfers", description = "Send money from your wallet")
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final WalletService walletService;

    @Operation(summary = "Transfer from my wallet to an account number or phone account number",
            description = "Send to an account number or a phone account number (mobile number without the leading 0). "
                    + "To pay a saved beneficiary use /transfers/beneficiaries/{beneficiaryId}. "
                    + "Requires the user's 4-digit transaction PIN. "
                    + "The source is always the logged-in user's account. Send an Idempotency-Key header "
                    + "to make retries safe: a repeated request with the same key and body returns the original "
                    + "transaction (200) instead of transferring again.")
    @ApiResponse(responseCode = "201", description = "Transfer completed")
    @ApiResponse(responseCode = "200", description = "Idempotent replay of an earlier transfer")
    @ApiResponse(responseCode = "403", description = "Incorrect PIN (the response says how many attempts are left)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "423", description = "PIN locked after too many incorrect attempts",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Destination account not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Idempotency key reused with a different body",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "Insufficient funds, own account, or inactive account",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(
            @Parameter(description = "Client-generated unique key (e.g. a UUID), max 64 chars")
            @RequestHeader(name = IdempotencyHeaders.KEY, required = false) @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransactionResult result = walletService.transfer(request, idempotencyKey);
        return IdempotencyHeaders.toResponse(result, HttpStatus.CREATED);
    }

    @Operation(summary = "Transfer from my wallet to one of my saved beneficiaries",
            description = "Pick the beneficiary id from GET /api/v1/me/beneficiaries. Requires the transaction PIN. "
                    + "Supports the same Idempotency-Key header as a normal transfer.")
    @ApiResponse(responseCode = "201", description = "Transfer completed")
    @ApiResponse(responseCode = "200", description = "Idempotent replay of an earlier transfer")
    @ApiResponse(responseCode = "403", description = "Incorrect PIN",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Not one of your beneficiaries",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "Insufficient funds or inactive account",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/beneficiaries/{beneficiaryId}")
    public ResponseEntity<TransactionResponse> transferToBeneficiary(
            @PathVariable @Positive Long beneficiaryId,
            @Parameter(description = "Client-generated unique key (e.g. a UUID), max 64 chars")
            @RequestHeader(name = IdempotencyHeaders.KEY, required = false) @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody BeneficiaryTransferRequest request) {
        TransactionResult result = walletService.transferToBeneficiary(beneficiaryId, request, idempotencyKey);
        return IdempotencyHeaders.toResponse(result, HttpStatus.CREATED);
    }
}
