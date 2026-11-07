package com.example.test.repo.spec;

import com.example.test.model.Account;
import com.example.test.model.Beneficiary;
import com.example.test.model.User;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import static com.example.test.repo.spec.SearchRanges.hasText;

public final class BeneficiarySpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private BeneficiarySpecifications() {
    }

    /** Restricts results to one customer's beneficiaries. Always applied. */
    public static Specification<Beneficiary> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    /** Case-insensitive partial match on nickname, holder's name, account number or phone account number. */
    public static Specification<Beneficiary> matching(String q) {
        return (root, query, cb) -> {
            if (!hasText(q)) {
                return cb.conjunction();
            }
            Join<Beneficiary, Account> account = root.join("account");
            Join<Account, User> user = account.join("user");
            String pattern = SearchRanges.likePattern(q);
            return cb.or(
                    cb.like(cb.lower(root.<String>get("nickname")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(user.<String>get("firstName")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(user.<String>get("lastName")), pattern, LIKE_ESCAPE),
                    cb.like(account.<String>get("accountNumber"), pattern, LIKE_ESCAPE),
                    cb.like(account.<String>get("phoneAccountNumber"), pattern, LIKE_ESCAPE));
        };
    }
}
