package com.example.test.repo;

import com.example.test.model.LedgerEntry;
import com.example.test.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface LedgerEntryRepo extends JpaRepository<LedgerEntry, Long>, JpaSpecificationExecutor<LedgerEntry> {

    /** Statement search; the parent transaction is fetched in the same query to avoid N+1 selects. */
    @Override
    @EntityGraph(attributePaths = "transaction")
    Page<LedgerEntry> findAll(Specification<LedgerEntry> spec, Pageable pageable);

    List<LedgerEntry> findByTransactionOrderByIdAsc(WalletTransaction transaction);
}
