package com.example.test.controller;

import com.example.test.dto.TransactionResponse;
import com.example.test.service.AccountQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transactions", description = "Transaction lookup")
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final AccountQueryService accountQueryService;

    @Operation(summary = "Get a transaction I sent or received, by reference")
    @GetMapping("/{reference}")
    public TransactionResponse getTransaction(@PathVariable @Size(max = 40) String reference) {
        return accountQueryService.getTransaction(reference);
    }
}
