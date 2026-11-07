package com.example.test.service;

import com.example.test.TestAccounts;
import com.example.test.common.BusinessException;
import com.example.test.common.ErrorCode;
import com.example.test.dto.DoTransDto;
import com.example.test.dto.AccountResponse;
import com.example.test.repo.AccountRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs real concurrent transfers (no test-managed transaction) to prove the row-locking strategy:
 * no lost updates, no overdrafts and no deadlocks between opposite-direction transfers.
 */
@SpringBootTest
class ConcurrentTransferIntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    WalletService walletService;
    @Autowired
    AccountRepo accountRepo;

    @Test
    void concurrentDebitsNeverOverdrawTheSourceWallet() throws Exception {
        AccountResponse sender = TestAccounts.create(walletService);
        AccountResponse receiver = TestAccounts.create(walletService);
        walletService.fund(sender.accountNumber(), new BigDecimal("1000.00"), null, null);

        // 150 attempts of 10.00 against a 1000.00 balance: exactly 100 can succeed.
        DoTransDto request = new DoTransDto(sender.accountNumber(), receiver.accountNumber(),
                new BigDecimal("10.00"));
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Throwable> unexpected = runConcurrently(150, i -> {
            try {
                walletService.transfer(request, null);
                succeeded.incrementAndGet();
            } catch (BusinessException ex) {
                if (ex.getErrorCode() != ErrorCode.INSUFFICIENT_FUNDS) {
                    throw ex;
                }
                insufficient.incrementAndGet();
            }
        });

        assertThat(unexpected).isEmpty();
        assertThat(succeeded.get()).isEqualTo(100);
        assertThat(insufficient.get()).isEqualTo(50);
        assertThat(balanceOf(sender)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("1000.00");
    }

    @Test
    void oppositeDirectionTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        AccountResponse a = TestAccounts.create(walletService);
        AccountResponse b = TestAccounts.create(walletService);
        walletService.fund(a.accountNumber(), new BigDecimal("500.00"), null, null);
        walletService.fund(b.accountNumber(), new BigDecimal("500.00"), null, null);

        DoTransDto aToB = new DoTransDto(a.accountNumber(), b.accountNumber(), new BigDecimal("5.00"));
        DoTransDto bToA = new DoTransDto(b.accountNumber(), a.accountNumber(), new BigDecimal("3.00"));
        List<Throwable> unexpected = runConcurrently(100,
                i -> walletService.transfer(i % 2 == 0 ? aToB : bToA, null));

        assertThat(unexpected).isEmpty();
        // 50 x 5.00 moved A->B and 50 x 3.00 moved B->A.
        assertThat(balanceOf(a)).isEqualByComparingTo("400.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("600.00");
    }

    private List<Throwable> runConcurrently(int tasks, IndexedTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < tasks; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        task.run(index);
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return failures;
    }

    private BigDecimal balanceOf(AccountResponse account) {
        return accountRepo.findByAccountNumber(account.accountNumber()).orElseThrow()
                .getWalletBalance().getAmount();
    }

    @FunctionalInterface
    private interface IndexedTask {
        void run(int index);
    }
}
