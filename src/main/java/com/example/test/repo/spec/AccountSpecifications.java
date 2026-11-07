package com.example.test.repo.spec;

import com.example.test.dto.search.AccountSearchCriteria;
import com.example.test.model.Account;
import com.example.test.model.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.example.test.repo.spec.SearchRanges.endExclusiveOf;
import static com.example.test.repo.spec.SearchRanges.hasText;
import static com.example.test.repo.spec.SearchRanges.startOf;

public final class AccountSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private AccountSpecifications() {
    }

    /** Builds one AND-ed predicate from whichever filters were supplied. */
    public static Specification<Account> matching(AccountSearchCriteria c) {
        SearchRanges.validate(null, null, c.fromDate(), c.toDate());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(c.q())) {
                Join<Account, User> user = root.join("user");
                String pattern = SearchRanges.likePattern(c.q());
                predicates.add(cb.or(
                        cb.like(cb.lower(user.<String>get("firstName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(user.<String>get("lastName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(user.<String>get("email")), pattern, LIKE_ESCAPE),
                        cb.like(user.<String>get("phoneNumber"), pattern, LIKE_ESCAPE),
                        cb.like(root.<String>get("accountNumber"), pattern, LIKE_ESCAPE),
                        cb.like(root.<String>get("phoneAccountNumber"), pattern, LIKE_ESCAPE)));
            }
            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
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
