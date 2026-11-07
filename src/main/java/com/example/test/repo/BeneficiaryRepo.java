package com.example.test.repo;

import com.example.test.model.Beneficiary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface BeneficiaryRepo extends JpaRepository<Beneficiary, Long>, JpaSpecificationExecutor<Beneficiary> {

    /** The holder's name is shown for every beneficiary, so account and owner are fetched in the same query. */
    @Override
    @EntityGraph(attributePaths = {"account", "account.user"})
    Page<Beneficiary> findAll(Specification<Beneficiary> spec, Pageable pageable);

    /** Scoped to the owner, so one customer can never read or use another customer's beneficiary. */
    @EntityGraph(attributePaths = {"account"})
    Optional<Beneficiary> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndAccountId(Long userId, Long accountId);
}
