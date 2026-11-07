package com.example.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Transfer to one of the caller's saved beneficiaries; the beneficiary id is in the path. */
public record BeneficiaryTransferRequest(

        @Schema(example = "2500.00")
        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "must have at most 17 integer digits and 2 decimal places")
        BigDecimal amount,

        @Schema(example = "Rent for November")
        @Size(max = 100)
        String narration,

        @Schema(example = "2580", description = "Your 4-digit transaction PIN")
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits")
        String pin) {

    @Override
    public String toString() {
        return "BeneficiaryTransferRequest[amount=" + amount + ", narration=" + narration + ", pin=****]";
    }
}
