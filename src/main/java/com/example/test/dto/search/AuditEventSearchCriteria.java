package com.example.test.dto.search;

import com.example.test.model.AuditAction;
import com.example.test.model.AuditOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Admin audit-trail search. Every filter is optional; supplied filters are AND-ed together. */
public record AuditEventSearchCriteria(

        @Positive
        Long actorUserId,

        AuditAction action,

        AuditOutcome outcome,

        @Schema(description = "e.g. ACCOUNT, TRANSACTION, USER, BENEFICIARY")
        @Size(max = 30)
        String resourceType,

        @Schema(description = "e.g. an account number or a transaction reference")
        @Size(max = 64)
        String resourceId,

        @Schema(description = "The X-Request-Id of the request that produced the event")
        @Size(max = 64)
        String requestId,

        @Schema(description = "Inclusive start date (yyyy-MM-dd, Africa/Lagos)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Schema(description = "Inclusive end date (yyyy-MM-dd, Africa/Lagos)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate) {
}
