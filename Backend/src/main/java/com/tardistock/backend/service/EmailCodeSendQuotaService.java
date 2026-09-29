package com.tardistock.backend.service;

import com.tardistock.backend.entity.EmailCodeSendQuota;
import com.tardistock.backend.repository.EmailCodeSendQuotaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Caps how many 6-digit codes can be mailed to one address per 24 hours,
 * shared across signup verification, password reset and account security.
 *
 * Each resend resets the per-code attempt counter, so without this cap the
 * 5-attempt limit could be sidestepped by resending every minute.
 */
@Service
public class EmailCodeSendQuotaService {

    static final Duration WINDOW = Duration.ofHours(24);

    private final EmailCodeSendQuotaRepository quotaRepository;
    private final int dailyLimit;

    public EmailCodeSendQuotaService(
            EmailCodeSendQuotaRepository quotaRepository,
            @Value("${email-code.daily-send-limit:10}") int dailyLimit) {
        if (dailyLimit < 1) {
            throw new IllegalArgumentException(
                    "email-code.daily-send-limit must be at least 1");
        }
        this.quotaRepository = quotaRepository;
        this.dailyLimit = dailyLimit;
    }

    /**
     * Records one send for {@code email} if the quota allows it.
     * Must run inside the caller's transaction so a failed mail send rolls
     * the increment back together with the stored code.
     *
     * @return false when the address already reached its 24-hour limit
     */
    @Transactional
    public boolean tryConsume(String email, LocalDateTime now) {
        EmailCodeSendQuota quota = quotaRepository
                .findByEmailForUpdate(email)
                .orElseGet(() -> new EmailCodeSendQuota(email, now));

        if (!now.isBefore(quota.getWindowStartedAt().plus(WINDOW))) {
            quota.setWindowStartedAt(now);
            quota.setSendCount(0);
        }
        if (quota.getSendCount() >= dailyLimit) {
            return false;
        }

        quota.setSendCount(quota.getSendCount() + 1);
        quotaRepository.save(quota);
        return true;
    }
}
