package com.example.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Transfer request (name kept from the supplied starter's {@code ServiceCall} contract). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoTransDto {

    @Schema(example = "1234567890")
    @NotBlank
    @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits")
    private String fromAccount;

    @Schema(example = "9876543210")
    @NotBlank
    @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits")
    private String toAccount;

    @Schema(example = "2500.00")
    @NotNull
    @DecimalMin(value = "0.01", message = "must be at least 0.01")
    @Digits(integer = 17, fraction = 2, message = "must have at most 17 integer digits and 2 decimal places")
    private BigDecimal amount;

    @Schema(example = "Rent for November")
    @Size(max = 100)
    private String narration;

    public DoTransDto(String fromAccount, String toAccount, BigDecimal amount) {
        this(fromAccount, toAccount, amount, null);
    }
}
