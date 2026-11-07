package com.example.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddBeneficiaryRequest(

        @Schema(example = "7068565534", description = "Account number or phone account number")
        @NotBlank
        @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits")
        String accountNumber,

        @Schema(example = "Mum")
        @Size(max = 50)
        String nickname) {
}
