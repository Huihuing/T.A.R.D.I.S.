package com.tardistock.backend.service;

/**
 * An emailed verification code expired or exceeded its attempt limit.
 * Transactions must commit on this exception so the stale code cleanup is kept.
 */
public class VerificationCodeExpiredException
        extends IllegalStateException {

    public VerificationCodeExpiredException(String message) {
        super(message);
    }
}
