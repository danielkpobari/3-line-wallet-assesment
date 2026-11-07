package com.example.test.repo;

import com.example.test.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepo extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    /** Owner and balance are needed by every account response, so they are fetched in the same query. */
    @EntityGraph(attributePaths = {"user", "walletBalance"})
    Optional<Account> findByAccountNumber(String accountNumber);

    @EntityGraph(attributePaths = {"user", "walletBalance"})
    Optional<Account> findByUserId(Long userId);

    @Override
    @EntityGraph(attributePaths = {"user", "walletBalance"})
    Page<Account> findAll(Specification<Account> spec, Pageable pageable);

    boolean existsByAccountNumber(String accountNumber);

    /** Used by the generator: a candidate must not clash with either kind of number. */
    boolean existsByAccountNumberOrPhoneAccountNumber(String accountNumber, String phoneAccountNumber);

    /** Finds an account by its generated account number or by its phone account number. */
    @EntityGraph(attributePaths = {"user", "walletBalance"})
    @Query("select a from Account a where a.accountNumber = :number or a.phoneAccountNumber = :number")
    Optional<Account> findByAnyNumber(@Param("number") String number);

    /** Resolves either kind of number to the canonical (generated) account number. */
    @Query("select a.accountNumber from Account a where a.accountNumber = :number or a.phoneAccountNumber = :number")
    Optional<String> resolveAccountNumber(@Param("number") String number);

    /**
     * Loads the account and its wallet balance with a {@code SELECT ... FOR UPDATE} row lock.
     * Callers that lock more than one account must lock them in a deterministic order to avoid deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a join fetch a.walletBalance where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    /** Resolves only the account number of a user's wallet (used for ownership checks). */
    @Query("select a.accountNumber from Account a where a.user.id = :userId")
    Optional<String> findAccountNumberByUserId(@Param("userId") Long userId);
}
