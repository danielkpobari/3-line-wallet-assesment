package com.example.test.model;

/** Everything the audit trail records. */
public enum AuditAction {
    USER_REGISTERED,
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    PIN_VERIFICATION_FAILED,
    PIN_LOCKED,
    PIN_CHANGED,
    TRANSFER_COMPLETED,
    TRANSFER_FAILED,
    WALLET_FUNDED,
    ACCOUNT_STATUS_CHANGED,
    BENEFICIARY_ADDED,
    BENEFICIARY_REMOVED
}
