package com.example.test.dto.search;

import com.example.test.model.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Admin customer/account search. Every filter is optional; supplied filters are AND-ed together. */
public record AccountSearchCriteria(

        @Schema(description = "Case-insensitive partial match on name, email, phone number, account number "
                + "or phone account number")
        @Size(max = 100)
        String q,

        AccountStatus status,

        @Schema(description = "Accounts opened on or after this date (yyyy-MM-dd, Africa/Lagos)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Schema(description = "Accounts opened on or before this date (yyyy-MM-dd, Africa/Lagos)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate) {
}
