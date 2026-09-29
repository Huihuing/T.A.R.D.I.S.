package com.tardistock.backend.service;

/**
 * An emailed verification code was wrong after its failed-attempt counter was
 * incremented. Transactions must commit on this exception, otherwise the
 * counter update is rolled back and the 5-attempt limit is never enforced.
 */
public class VerificationCodeRejectedException
        extends IllegalArgumentException {

    public VerificationCodeRejectedException(String message) {
        super(message);
    }
}
