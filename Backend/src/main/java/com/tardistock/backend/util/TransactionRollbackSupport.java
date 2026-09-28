package com.tardistock.backend.util;

import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Keeps controller-level error responses from accidentally committing a
 * transaction after a caught runtime failure.
 */
public final class TransactionRollbackSupport {

    private TransactionRollbackSupport() {}

    public static void markRollbackOnlyIfActive() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }

        try {
            TransactionAspectSupport.currentTransactionStatus()
                    .setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // Direct unit tests can call controllers without a transaction
            // interceptor. There is nothing to roll back in that case.
        }
    }
}
