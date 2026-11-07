package com.example.test.repo;

import com.example.test.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface WalletTransactionRepo extends JpaRepository<WalletTransaction, Long>,
        JpaSpecificationExecutor<WalletTransaction> {

    Optional<WalletTransaction> findByReference(String reference);

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);
}
