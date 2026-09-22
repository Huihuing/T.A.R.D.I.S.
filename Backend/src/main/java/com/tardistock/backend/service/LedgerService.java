package com.tardistock.backend.service;

import com.tardistock.backend.entity.LedgerEntry;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.LedgerEntryRepository;
import com.tardistock.backend.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class LedgerService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LedgerEntryRepository ledgerEntryRepository;
    private final MemberRepository memberRepository;

    public LedgerService(
            LedgerEntryRepository ledgerEntryRepository,
            MemberRepository memberRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.memberRepository = memberRepository;
    }

    public LedgerEntry record(
            Member member,
            String type,
            double amount,
            double balanceAfter,
            String description) {
        return record(
                member,
                type,
                amount,
                balanceAfter,
                null,
                null,
                description
        );
    }

    public LedgerEntry record(
            Member member,
            String type,
            double amount,
            double balanceAfter,
            String counterparty,
            String symbol,
            String description) {
        return ledgerEntryRepository.save(new LedgerEntry(
                member,
                type,
                roundMoney(amount),
                roundMoney(balanceAfter),
                normalize(counterparty),
                normalize(symbol),
                normalize(description),
                LocalDateTime.now(KST)
        ));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> recent(String username) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return ledgerEntryRepository.findTop100ByMemberOrderByCreatedAtDesc(member);
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
