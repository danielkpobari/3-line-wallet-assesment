package com.example.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FundWalletRequest(

        @Schema(example = "10000.00")
        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "must have at most 17 integer digits and 2 decimal places")
        BigDecimal amount,

        @Schema(example = "Test top-up")
        @Size(max = 100)
        String narration) {
}
