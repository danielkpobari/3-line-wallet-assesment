package com.example.test.repo.spec;

import com.example.test.common.BusinessException;
import com.example.test.common.ErrorCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Shared range handling for search filters. Dates are business days in Lagos time. */
final class SearchRanges {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Lagos");

    private SearchRanges() {
    }

    static void validate(BigDecimal minAmount, BigDecimal maxAmount, LocalDate fromDate, LocalDate toDate) {
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "minAmount must not be greater than maxAmount");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "fromDate must not be after toDate");
        }
    }

    /** Start of {@code date} (inclusive lower bound). */
    static Instant startOf(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    /** Start of the day after {@code date} (exclusive upper bound), so the whole of {@code date} is included. */
    static Instant endExclusiveOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    static String likePattern(String text) {
        String escaped = text.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
