package com.example.test.model;

/**
 * Transfers are applied synchronously inside one database transaction, so a persisted
 * transaction is always SUCCESSFUL. Failed attempts are rolled back and not stored.
 */
public enum TransactionStatus {
    SUCCESSFUL
}
