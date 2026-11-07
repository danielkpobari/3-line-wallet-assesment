package com.example.test.service;

import com.example.test.common.BusinessException;
import com.example.test.common.Emails;
import com.example.test.common.ErrorCode;
import com.example.test.common.PhoneNumbers;
import com.example.test.dto.AccountResponse;
import com.example.test.dto.BeneficiaryTransferRequest;
import com.example.test.dto.DoTransDto;
import com.example.test.dto.RegisterRequest;
import com.example.test.dto.TransactionResponse;
import com.example.test.dto.TransferRequest;
import com.example.test.model.Account;
import com.example.test.model.AccountStatus;
import com.example.test.model.AuditAction;
import com.example.test.model.LedgerEntry;
import com.example.test.model.Role;
import com.example.test.model.TransactionType;
import com.example.test.model.User;
import com.example.test.model.WalletBalance;
import com.example.test.model.WalletTransaction;
import com.example.test.repo.AccountRepo;
import com.example.test.repo.LedgerEntryRepo;
import com.example.test.repo.UserRepo;
import com.example.test.repo.WalletTransactionRepo;
import com.example.test.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Write side of the wallet: onboarding, money movement and account administration.
 *
 * <p>Every customer has two numbers that receive into the same wallet: the generated account number and the
 * phone account number (mobile number without the leading 0). Inputs may use either; everything stored uses
 * the generated one.
 *
 * <p>Every money movement runs in a single database transaction that (1) row-locks the affected
 * accounts in ascending account-number order, (2) updates balances, and (3) writes one
 * {@link WalletTransaction} plus its {@link LedgerEntry} rows. Either all of it commits or none of it does.
 *
 * <p>Public methods return DTOs, so no entity (or lazy proxy) leaves the transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService implements ServiceCall {

    static final String DEFAULT_CURRENCY = "NGN";
    private static final String TRANSFER_PREFIX = "TRF";
    private static final String FUNDING_PREFIX = "FND";
    private static final String RESOURCE_ACCOUNT = "ACCOUNT";
    private static final String RESOURCE_TRANSACTION = "TRANSACTION";

    private final UserRepo userRepo;
    private final AccountRepo accountRepo;
    private final WalletTransactionRepo transactionRepo;
    private final LedgerEntryRepo ledgerEntryRepo;
    private final AccountNumberGenerator accountNumberGenerator;
    private final ReferenceGenerator referenceGenerator;
    private final PasswordEncoder passwordEncoder;
    private final PinService pinService;
    private final BeneficiaryService beneficiaryService;
    private final AuditService audit;

    // ------------------------------------------------------------------ onboarding

    /**
     * Registers a customer: validates and hashes the transaction PIN, hashes the password, then creates the user,
     * a 10-digit account and an NGN wallet.
     */
    @Transactional
    public AccountResponse register(RegisterRequest request) {
        User user = new User(request.firstName(), request.lastName(), request.email(), request.phoneNumber(),
                passwordEncoder.encode(request.password()), pinService.hashNewPin(request.pin()), Role.USER);
        AccountResponse account = AccountResponse.from(createCustomer(user));
        audit.event(AuditAction.USER_REGISTERED)
                .actor(account.userId(), Role.USER)
                .resource(RESOURCE_ACCOUNT, account.accountNumber())
                .detail("phoneAccountNumber", account.phoneAccountNumber())
                .recordSuccess();
        return account;
    }

    /** Starter-contract entry point. The supplied user must already carry password and PIN hashes. */
    @Override
    @Transactional
    public void createUserAndAccount(User user) {
        createCustomer(user);
    }

    private Account createCustomer(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "User details are required");
        }
        String email = Emails.normalise(requireText(user.getEmail(), "Email"));
        PhoneNumbers.NigerianMobile phone = PhoneNumbers.parse(user.getPhoneNumber());
        if (userRepo.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "A user with this email already exists");
        }
        if (userRepo.existsByPhoneNumber(phone.e164())) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE, "A user with this phone number already exists");
        }

        User savedUser = userRepo.save(new User(
                requireText(user.getFirstName(), "First name"),
                requireText(user.getLastName(), "Last name"),
                email,
                phone.e164(),
                requireText(user.getPasswordHash(), "Password"),
                requireText(user.getPinHash(), "Transaction PIN"),
                Role.USER));
        Account account = accountRepo.save(new Account(
                accountNumberGenerator.generate(), phone.accountNumber(), savedUser, WalletBalance.empty(),
                DEFAULT_CURRENCY));

        log.debug("Created user id={} with account {} (phone account {})",
                savedUser.getId(), account.getAccountNumber(), account.getPhoneAccountNumber());
        return account;
    }

    // ------------------------------------------------------------------ transfers

    /**
     * Transfers from the logged-in user's own wallet to an account number or phone account number, after
     * verifying their transaction PIN. The source account is resolved from the logged-in user
     * ({@link CurrentUser#get()}), never taken from the request, so a caller cannot debit another customer's wallet.
     */
    @Transactional
    public TransactionResult transfer(TransferRequest request, String idempotencyKey) {
        return transferFromCurrentUser(request.toAccount(), null, request.amount(), request.narration(),
                request.pin(), request.shouldSaveBeneficiary(), idempotencyKey);
    }

    /** Same as {@link #transfer(TransferRequest, String)}, to one of the logged-in user's saved beneficiaries. */
    @Transactional
    public TransactionResult transferToBeneficiary(Long beneficiaryId, BeneficiaryTransferRequest request,
                                                   String idempotencyKey) {
        return transferFromCurrentUser(null, beneficiaryId, request.amount(), request.narration(), request.pin(),
                false, idempotencyKey);
    }

    /** Exactly one of {@code toAccount} / {@code beneficiaryId} is set by the two public entry points above. */
    private TransactionResult transferFromCurrentUser(String toAccount, Long beneficiaryId, BigDecimal amount,
                                                      String narration, String pin, boolean saveAsBeneficiary,
                                                      String idempotencyKey) {
        CurrentUser sender = CurrentUser.get();
        try {
            String sourceAccount = accountRepo.findAccountNumberByUserId(sender.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "You do not have a wallet"));
            String destination = beneficiaryId != null
                    ? beneficiaryService.accountNumberOf(beneficiaryId)
                    : requireText(toAccount, "Destination account");
            // Separate transaction: a wrong PIN is counted even though this transfer then rolls back.
            pinService.verify(pin);

            TransactionResult result = transfer(new DoTransDto(sourceAccount, destination, amount, narration),
                    idempotencyKey);

            if (saveAsBeneficiary && !result.replayed()) {
                beneficiaryService.saveIfAbsent(result.transaction().toAccount());
            }
            TransactionResponse tx = result.transaction();
            audit.event(AuditAction.TRANSFER_COMPLETED)
                    .actor(sender)
                    .resource(RESOURCE_TRANSACTION, tx.reference())
                    .detail("fromAccount", tx.fromAccount())
                    .detail("toAccount", tx.toAccount())
                    .detail("amount", tx.amount())
                    .detail("beneficiaryId", beneficiaryId)
                    .detail("replayed", result.replayed() ? Boolean.TRUE : null)
                    .recordSuccess();
            return result;
        } catch (BusinessException ex) {
            // The transfer rolls back, but the failed attempt must stay on record.
            audit.event(AuditAction.TRANSFER_FAILED)
                    .actor(sender)
                    .detail("toAccount", toAccount)
                    .detail("beneficiaryId", beneficiaryId)
                    .detail("amount", amount)
                    .recordFailureDespiteRollback(ex);
            throw ex;
        }
    }

    /** Starter-contract entry point. */
    @Override
    @Transactional
    public void doIntraTransfer(DoTransDto request) {
        transfer(request, null);
    }

    /**
     * Moves money between two wallets. Either side may be given as an account number or a phone account number;
     * both are resolved to the canonical account number, which is what the transaction and ledger record.
     * Callers are responsible for having established that the source belongs to whoever initiated the transfer
     * (see {@link #transfer(TransferRequest, String)}).
     *
     * @param idempotencyKey optional client key; repeating a request with the same key and payload
     *                       returns the original transaction instead of transferring twice
     */
    @Transactional
    public TransactionResult transfer(DoTransDto request, String idempotencyKey) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Transfer details are required");
        }
        BigDecimal amount = normalizeAmount(request.getAmount());
        String from = resolveAccountNumber(requireText(request.getFromAccount(), "Source account"));
        String to = resolveAccountNumber(requireText(request.getToAccount(), "Destination account"));
        String narration = blankToNull(request.getNarration());
        String key = blankToNull(idempotencyKey);

        if (from.equals(to)) {
            throw new BusinessException(ErrorCode.SAME_ACCOUNT_TRANSFER,
                    "Source and destination accounts must be different");
        }

        Optional<TransactionResult> replay = findReplay(key, TransactionType.TRANSFER, from, to, amount);
        if (replay.isPresent()) {
            return replay.get();
        }

        // Lock both rows in a fixed order so two opposite transfers (A->B, B->A) cannot deadlock.
        boolean fromFirst = from.compareTo(to) < 0;
        Account firstLocked = lockAccount(fromFirst ? from : to);
        Account secondLocked = lockAccount(fromFirst ? to : from);
        Account source = fromFirst ? firstLocked : secondLocked;
        Account destination = fromFirst ? secondLocked : firstLocked;

        // A concurrent request with the same key may have committed while we waited for the lock.
        replay = findReplay(key, TransactionType.TRANSFER, from, to, amount);
        if (replay.isPresent()) {
            return replay.get();
        }

        requireActive(source);
        requireActive(destination);
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new BusinessException(ErrorCode.CURRENCY_MISMATCH, "Accounts are in different currencies");
        }

        WalletBalance sourceBalance = source.getWalletBalance();
        WalletBalance destinationBalance = destination.getWalletBalance();
        if (!sourceBalance.canDebit(amount)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient wallet balance");
        }

        BigDecimal sourceBefore = sourceBalance.getAmount();
        BigDecimal destinationBefore = destinationBalance.getAmount();
        sourceBalance.debit(amount);
        destinationBalance.credit(amount);

        WalletTransaction tx = transactionRepo.save(WalletTransaction.transfer(
                referenceGenerator.next(TRANSFER_PREFIX), key, from, to, amount, source.getCurrency(), narration));
        ledgerEntryRepo.save(LedgerEntry.debit(tx, from, amount, sourceBefore, sourceBalance.getAmount()));
        ledgerEntryRepo.save(LedgerEntry.credit(tx, to, amount, destinationBefore, destinationBalance.getAmount()));

        log.debug("Transfer {} of {} {} from {} to {} applied", tx.getReference(), amount, tx.getCurrency(), from, to);
        return new TransactionResult(TransactionResponse.from(tx), false);
    }

    // ------------------------------------------------------------------ funding (demo only)

    /**
     * Credits a wallet without a real payment source. Exists so the transfer flow can be exercised
     * locally; in production a wallet is credited only from a verified payment/bank event.
     * The audit actor is the logged-in admin, or none when called by system code or tests.
     */
    @Transactional
    public TransactionResult fund(String accountNumber, BigDecimal amount, String narration, String idempotencyKey) {
        BigDecimal value = normalizeAmount(amount);
        String target = resolveAccountNumber(requireText(accountNumber, "Account number"));
        String key = blankToNull(idempotencyKey);

        Optional<TransactionResult> replay = findReplay(key, TransactionType.FUNDING, null, target, value);
        if (replay.isPresent()) {
            return replay.get();
        }

        Account account = lockAccount(target);

        replay = findReplay(key, TransactionType.FUNDING, null, target, value);
        if (replay.isPresent()) {
            return replay.get();
        }
        requireActive(account);

        WalletBalance balance = account.getWalletBalance();
        BigDecimal before = balance.getAmount();
        balance.credit(value);

        WalletTransaction tx = transactionRepo.save(WalletTransaction.funding(
                referenceGenerator.next(FUNDING_PREFIX), key, target, value, account.getCurrency(),
                blankToNull(narration)));
        ledgerEntryRepo.save(LedgerEntry.credit(tx, target, value, before, balance.getAmount()));

        audit.event(AuditAction.WALLET_FUNDED)
                .actor(CurrentUser.find().orElse(null))
                .resource(RESOURCE_TRANSACTION, tx.getReference())
                .detail("toAccount", target)
                .detail("amount", value)
                .recordSuccess();
        return new TransactionResult(TransactionResponse.from(tx), false);
    }

    // ------------------------------------------------------------------ administration

    /**
     * Freezes, closes or re-activates an account. Inactive accounts can neither send nor receive.
     * The audit actor is the logged-in admin.
     */
    @Transactional
    public AccountResponse changeStatus(String accountNumber, AccountStatus status) {
        Account account = lockAccount(resolveAccountNumber(requireText(accountNumber, "Account number")));
        AccountStatus previous = account.getStatus();
        account.changeStatus(status);
        audit.event(AuditAction.ACCOUNT_STATUS_CHANGED)
                .actor(CurrentUser.find().orElse(null))
                .resource(RESOURCE_ACCOUNT, account.getAccountNumber())
                .detail("from", previous)
                .detail("to", status)
                .recordSuccess();
        return AccountResponse.from(account);
    }

    // ------------------------------------------------------------------ helpers

    private Optional<TransactionResult> findReplay(String key, TransactionType type, String from, String to,
                                                   BigDecimal amount) {
        if (key == null) {
            return Optional.empty();
        }
        return transactionRepo.findByIdempotencyKey(key).map(existing -> {
            boolean samePayload = existing.getType() == type
                    && Objects.equals(existing.getFromAccount(), from)
                    && existing.getToAccount().equals(to)
                    && existing.getAmount().compareTo(amount) == 0;
            if (!samePayload) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency key was already used for a different request");
            }
            log.info("Idempotent replay of transaction {}", existing.getReference());
            return new TransactionResult(TransactionResponse.from(existing), true);
        });
    }

    /** Maps an account number or phone account number to the canonical account number. */
    private String resolveAccountNumber(String number) {
        return accountRepo.resolveAccountNumber(number)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account " + number + " was not found"));
    }

    private Account lockAccount(String accountNumber) {
        return accountRepo.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account " + accountNumber + " was not found"));
    }

    private static void requireActive(Account account) {
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE,
                    "Account " + account.getAccountNumber() + " is " + account.getStatus());
        }
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Amount must have at most two decimal places");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
