package com.example.test.controller;

import com.example.test.common.ApiError;
import com.example.test.dto.AccountResponse;
import com.example.test.dto.BalanceResponse;
import com.example.test.dto.ChangePinRequest;
import com.example.test.dto.PageResponse;
import com.example.test.dto.StatementEntryResponse;
import com.example.test.dto.search.StatementSearchCriteria;
import com.example.test.service.AccountQueryService;
import com.example.test.service.PinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me", description = "The logged-in user's profile, balance, statement and transaction PIN")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final AccountQueryService accountQueryService;
    private final PinService pinService;

    @Operation(summary = "Get my profile and account")
    @GetMapping
    public AccountResponse me() {
        return accountQueryService.getMyAccount();
    }

    @Operation(summary = "Get my wallet balance")
    @GetMapping("/balance")
    public BalanceResponse balance() {
        return accountQueryService.getMyBalance();
    }

    @Operation(summary = "Search my statement (newest first)",
            description = "All filters are optional, e.g. ?entryType=DEBIT&fromDate=2026-11-01&minAmount=1000")
    @GetMapping("/transactions")
    public PageResponse<StatementEntryResponse> statement(
            @Valid @ParameterObject StatementSearchCriteria criteria,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_SIZE) @Min(1) @Max(ApiConstraints.MAX_PAGE_SIZE)
            int size) {
        return accountQueryService.searchMyStatement(criteria, page, size);
    }

    @Operation(summary = "Change my transaction PIN",
            description = "A wrong current PIN counts towards the lockout, the same as a wrong PIN on a transfer.")
    @ApiResponse(responseCode = "204", description = "PIN changed")
    @ApiResponse(responseCode = "403", description = "Current PIN is incorrect",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "423", description = "PIN locked after too many incorrect attempts",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PutMapping("/pin")
    public ResponseEntity<Void> changePin(@Valid @RequestBody ChangePinRequest request) {
        pinService.change(request.currentPin(), request.newPin());
        return ResponseEntity.noContent().build();
    }
}
