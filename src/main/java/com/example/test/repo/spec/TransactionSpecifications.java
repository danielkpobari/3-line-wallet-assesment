package com.example.test.repo.spec;

import com.example.test.dto.search.TransactionSearchCriteria;
import com.example.test.model.WalletTransaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.example.test.repo.spec.SearchRanges.endExclusiveOf;
import static com.example.test.repo.spec.SearchRanges.hasText;
import static com.example.test.repo.spec.SearchRanges.startOf;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /** Builds one AND-ed predicate from whichever filters were supplied. */
    public static Specification<WalletTransaction> matching(TransactionSearchCriteria c) {
        SearchRanges.validate(c.minAmount(), c.maxAmount(), c.fromDate(), c.toDate());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(c.reference())) {
                predicates.add(cb.equal(root.get("reference"), c.reference().trim()));
            }
            if (c.type() != null) {
                predicates.add(cb.equal(root.get("type"), c.type()));
            }
            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }
            if (hasText(c.accountNumber())) {
                predicates.add(cb.or(
                        cb.equal(root.get("fromAccount"), c.accountNumber()),
                        cb.equal(root.get("toAccount"), c.accountNumber())));
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
