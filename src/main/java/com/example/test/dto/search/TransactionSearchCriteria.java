package com.example.test.dto.search;

import com.example.test.model.TransactionStatus;
import com.example.test.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Admin transaction search. Every filter is optional; supplied filters are AND-ed together. */
public record TransactionSearchCriteria(

        @Schema(description = "Exact transaction reference")
        @Size(max = 40)
        String reference,

        TransactionType type,

        TransactionStatus status,

        @Schema(description = "Account number or phone account number; matches either side of the transaction")
        @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits")
        String accountNumber,

        @DecimalMin("0.00")
        BigDecimal minAmount,

        @DecimalMin("0.00")
        BigDecimal maxAmount,

        @Schema(description = "Inclusive start date (yyyy-MM-dd, Africa/Lagos)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Schema(description = "Inclusive end date (yyyy-MM-dd, Africa/Lagos)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate) {

    /** Copy with the account filter replaced (used to turn a phone account number into the canonical one). */
    public TransactionSearchCriteria withAccountNumber(String canonicalAccountNumber) {
        return new TransactionSearchCriteria(reference, type, status, canonicalAccountNumber,
                minAmount, maxAmount, fromDate, toDate);
    }
}
