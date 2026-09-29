package com.tardistock.backend.service;

import com.tardistock.backend.repository.EmailCodeSendQuotaRepository;

import static org.mockito.Mockito.mock;

final class SendQuotaTestSupport {

    private SendQuotaTestSupport() {}

    /** A real quota service whose repository never has a prior send. */
    static EmailCodeSendQuotaService unlimited() {
        return new EmailCodeSendQuotaService(
                mock(EmailCodeSendQuotaRepository.class),
                10
        );
    }
}
