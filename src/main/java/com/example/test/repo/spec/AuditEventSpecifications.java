package com.example.test.repo.spec;

import com.example.test.dto.search.AuditEventSearchCriteria;
import com.example.test.model.AuditEvent;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.example.test.repo.spec.SearchRanges.endExclusiveOf;
import static com.example.test.repo.spec.SearchRanges.hasText;
import static com.example.test.repo.spec.SearchRanges.startOf;

public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    /** Builds one AND-ed predicate from whichever filters were supplied. */
    public static Specification<AuditEvent> matching(AuditEventSearchCriteria c) {
        SearchRanges.validate(null, null, c.fromDate(), c.toDate());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.actorUserId() != null) {
                predicates.add(cb.equal(root.get("actorUserId"), c.actorUserId()));
            }
            if (c.action() != null) {
                predicates.add(cb.equal(root.get("action"), c.action()));
            }
            if (c.outcome() != null) {
                predicates.add(cb.equal(root.get("outcome"), c.outcome()));
            }
            if (hasText(c.resourceType())) {
                predicates.add(cb.equal(root.get("resourceType"), c.resourceType().trim()));
            }
            if (hasText(c.resourceId())) {
                predicates.add(cb.equal(root.get("resourceId"), c.resourceId().trim()));
            }
            if (hasText(c.requestId())) {
                predicates.add(cb.equal(root.get("requestId"), c.requestId().trim()));
            }
            if (c.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), startOf(c.fromDate())));
            }
            if (c.toDate() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("occurredAt"), endExclusiveOf(c.toDate())));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
