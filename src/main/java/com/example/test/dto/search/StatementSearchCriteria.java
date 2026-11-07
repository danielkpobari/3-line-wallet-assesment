package com.example.test.dto.search;

import com.example.test.model.EntryType;
import com.example.test.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Filters for an account statement. Every filter is optional; supplied filters are AND-ed together. */
public record StatementSearchCriteria(

        @Schema(description = "DEBIT (money out) or CREDIT (money in)")
        EntryType entryType,

        TransactionType transactionType,

        @Schema(description = "Exact transaction reference")
        @Size(max = 40)
        String reference,

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

    public static StatementSearchCriteria none() {
        return new StatementSearchCriteria(null, null, null, null, null, null, null);
    }
}
