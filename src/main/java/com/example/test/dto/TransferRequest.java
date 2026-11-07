package com.example.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Transfer from the caller's own wallet to an account number or phone account number.
 *
 * <p>There is deliberately no source account field: the source is always the logged-in user's account, so a
 * caller can never debit someone else's wallet. To pay a saved beneficiary instead, use
 * {@code POST /api/v1/transfers/beneficiaries/{beneficiaryId}} with a {@link BeneficiaryTransferRequest}.
 */
public record TransferRequest(

        @Schema(example = "7068565534", description = "Recipient's account number or phone account number "
                + "(mobile number without the leading 0)")
        @NotBlank
        @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits")
        String toAccount,

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
        String pin,

        @Schema(example = "false", description = "Optional. Save the recipient as a beneficiary after a successful "
                + "transfer (ignored if already saved)")
        Boolean saveAsBeneficiary) {

    public boolean shouldSaveBeneficiary() {
        return Boolean.TRUE.equals(saveAsBeneficiary);
    }

    @Override
    public String toString() {
        return "TransferRequest[toAccount=" + toAccount + ", amount=" + amount + ", narration=" + narration
                + ", pin=****, saveAsBeneficiary=" + saveAsBeneficiary + "]";
    }
}
