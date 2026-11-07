package com.example.test.repo.spec;

import com.example.test.dto.search.StatementSearchCriteria;
import com.example.test.model.LedgerEntry;
import com.example.test.model.WalletTransaction;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.example.test.repo.spec.SearchRanges.endExclusiveOf;
import static com.example.test.repo.spec.SearchRanges.hasText;
import static com.example.test.repo.spec.SearchRanges.startOf;

public final class LedgerEntrySpecifications {

    private LedgerEntrySpecifications() {
    }

    /** Restricts a statement to one account. Always applied, so users can never see other accounts' entries. */
    public static Specification<LedgerEntry> forAccount(String accountNumber) {
        return (root, query, cb) -> cb.equal(root.get("accountNumber"), accountNumber);
    }

    /** Builds one AND-ed predicate from whichever filters were supplied. */
    public static Specification<LedgerEntry> matching(StatementSearchCriteria c) {
        SearchRanges.validate(c.minAmount(), c.maxAmount(), c.fromDate(), c.toDate());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.entryType() != null) {
                predicates.add(cb.equal(root.get("entryType"), c.entryType()));
            }
            if (c.transactionType() != null || hasText(c.reference())) {
                Join<LedgerEntry, WalletTransaction> tx = root.join("transaction");
                if (c.transactionType() != null) {
                    predicates.add(cb.equal(tx.get("type"), c.transactionType()));
                }
                if (hasText(c.reference())) {
                    predicates.add(cb.equal(tx.get("reference"), c.reference().trim()));
                }
            }
            if (c.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("amount"), c.minAmount()));
            }
            if (c.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("amount"), c.maxAmount()));
            }
            if (c.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), startOf(c.fromDate())));
            }
            if (c.toDate() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), endExclusiveOf(c.toDate())));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
