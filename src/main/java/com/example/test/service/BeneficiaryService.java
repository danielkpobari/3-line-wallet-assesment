package com.example.test.service;

import com.example.test.common.BusinessException;
import com.example.test.common.ErrorCode;
import com.example.test.dto.AddBeneficiaryRequest;
import com.example.test.dto.BeneficiaryResponse;
import com.example.test.dto.PageResponse;
import com.example.test.model.Account;
import com.example.test.model.AuditAction;
import com.example.test.model.Beneficiary;
import com.example.test.repo.AccountRepo;
import com.example.test.repo.BeneficiaryRepo;
import com.example.test.repo.spec.BeneficiarySpecifications;
import com.example.test.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A customer's saved recipients. Every operation is scoped to the logged-in user ({@link CurrentUser#get()}). */
@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private static final String RESOURCE_BENEFICIARY = "BENEFICIARY";

    private final BeneficiaryRepo beneficiaryRepo;
    private final AccountRepo accountRepo;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<BeneficiaryResponse> list(String q, int page, int size) {
        return PageResponse.from(
                beneficiaryRepo.findAll(
                        BeneficiarySpecifications.ownedBy(CurrentUser.get().id())
                                .and(BeneficiarySpecifications.matching(q)),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))),
                BeneficiaryResponse::from);
    }

    /** Saves an account (by account number or phone account number) as a beneficiary. */
    @Transactional
    public BeneficiaryResponse add(AddBeneficiaryRequest request) {
        CurrentUser currentUser = CurrentUser.get();
        Account account = accountRepo.findByAnyNumber(request.accountNumber())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account " + request.accountNumber() + " was not found"));
        if (account.getUser().getId().equals(currentUser.id())) {
            throw new BusinessException(ErrorCode.INVALID_BENEFICIARY,
                    "You cannot add your own account as a beneficiary");
        }
        if (beneficiaryRepo.existsByUserIdAndAccountId(currentUser.id(), account.getId())) {
            throw new BusinessException(ErrorCode.BENEFICIARY_EXISTS, "This account is already a beneficiary");
        }
        Beneficiary saved = beneficiaryRepo.save(new Beneficiary(currentUser.id(), account, blankToNull(request.nickname())));
        auditAdded(currentUser, saved);
        return BeneficiaryResponse.from(saved);
    }

    @Transactional
    public void delete(Long beneficiaryId) {
        CurrentUser currentUser = CurrentUser.get();
        Beneficiary beneficiary = findOwned(currentUser, beneficiaryId);
        beneficiaryRepo.delete(beneficiary);
        audit.event(AuditAction.BENEFICIARY_REMOVED)
                .actor(currentUser)
                .resource(RESOURCE_BENEFICIARY, String.valueOf(beneficiaryId))
                .detail("accountNumber", beneficiary.getAccount().getAccountNumber())
                .recordSuccess();
    }

    /** The canonical account number of one of the logged-in user's beneficiaries (for a transfer). */
    @Transactional
    public String accountNumberOf(Long beneficiaryId) {
        return findOwned(CurrentUser.get(), beneficiaryId).getAccount().getAccountNumber();
    }

    /** Saves the account as a beneficiary unless it already is one, or is the user's own (after a transfer). */
    @Transactional
    public void saveIfAbsent(String accountNumber) {
        CurrentUser currentUser = CurrentUser.get();
        accountRepo.findByAccountNumber(accountNumber)
                .filter(account -> !account.getUser().getId().equals(currentUser.id()))
                .filter(account -> !beneficiaryRepo.existsByUserIdAndAccountId(currentUser.id(), account.getId()))
                .ifPresent(account -> auditAdded(currentUser,
                        beneficiaryRepo.save(new Beneficiary(currentUser.id(), account, null))));
    }

    private void auditAdded(CurrentUser currentUser, Beneficiary beneficiary) {
        audit.event(AuditAction.BENEFICIARY_ADDED)
                .actor(currentUser)
                .resource(RESOURCE_BENEFICIARY, String.valueOf(beneficiary.getId()))
                .detail("accountNumber", beneficiary.getAccount().getAccountNumber())
                .recordSuccess();
    }

    private Beneficiary findOwned(CurrentUser currentUser, Long beneficiaryId) {
        // Another customer's beneficiary is reported as not found, so ids cannot be probed.
        return beneficiaryRepo.findByIdAndUserId(beneficiaryId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.BENEFICIARY_NOT_FOUND,
                        "Beneficiary " + beneficiaryId + " was not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
