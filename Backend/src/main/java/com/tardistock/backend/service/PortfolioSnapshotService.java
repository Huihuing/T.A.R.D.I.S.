package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import com.tardistock.backend.entity.PortfolioSnapshot;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.PortfolioSnapshotRepository;
import com.tardistock.backend.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PortfolioSnapshotService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long MIN_CAPTURE_MINUTES = 60;

    private final PortfolioSnapshotRepository snapshotRepository;
    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PortfolioRepository portfolioRepository;
    private final FinnhubPriceService finnhubPriceService;

    public PortfolioSnapshotService(
            PortfolioSnapshotRepository snapshotRepository,
            MemberRepository memberRepository,
            WalletRepository walletRepository,
            PortfolioRepository portfolioRepository,
            FinnhubPriceService finnhubPriceService) {
        this.snapshotRepository = snapshotRepository;
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.portfolioRepository = portfolioRepository;
        this.finnhubPriceService = finnhubPriceService;
    }

    public List<PortfolioSnapshot> captureAndList(
            String username,
            String range) {

        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다."
                        ));

        LocalDateTime now = LocalDateTime.now(KST);
        boolean shouldCapture = snapshotRepository
                .findTopByMemberOrderByCapturedAtDesc(member)
                .map(snapshot ->
                        snapshot.getCapturedAt()
                                .plusMinutes(MIN_CAPTURE_MINUTES)
                                .isBefore(now))
                .orElse(true);

        if (shouldCapture) {
            capture(member, now);
        }

        LocalDateTime cutoff = rangeCutoff(range, now);
        List<PortfolioSnapshot> snapshots =
                new ArrayList<>(
                        snapshotRepository
                                .findTop1000ByMemberOrderByCapturedAtDesc(
                                        member
                                )
                );

        if (cutoff != null) {
            snapshots.removeIf(snapshot ->
                    snapshot.getCapturedAt().isBefore(cutoff));
        }

        snapshots.sort(
                Comparator.comparing(
                        PortfolioSnapshot::getCapturedAt
                )
        );

        return snapshots;
    }

    @Transactional
    public PortfolioSnapshot capture(
            Member member,
            LocalDateTime capturedAt) {

        Wallet wallet = walletRepository.findByMember(member)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "지갑 정보를 찾을 수 없습니다."
                        ));

        double investedValue = 0.0;
        List<Portfolio> portfolio =
                portfolioRepository.findByMember(member);

        for (Portfolio item : portfolio) {
            double currentPrice =
                    finnhubPriceService.getPrice(item.getSymbol());

            if (!Double.isFinite(currentPrice)
                    || currentPrice <= 0) {
                currentPrice = item.getAveragePrice();
            }

            investedValue += item.getAmount() * currentPrice;
        }

        double cash = roundMoney(wallet.getBalance());
        investedValue = roundMoney(investedValue);
        double total = roundMoney(cash + investedValue);

        PortfolioSnapshot snapshot = new PortfolioSnapshot(
                member,
                cash,
                investedValue,
                total,
                capturedAt
        );
        return snapshotRepository.save(snapshot);
    }

    private LocalDateTime rangeCutoff(
            String rawRange,
            LocalDateTime now) {

        String range = rawRange == null
                ? "1W"
                : rawRange.trim().toUpperCase();

        return switch (range) {
            case "1D" -> now.minusDays(1);
            case "1W" -> now.minusWeeks(1);
            case "1M" -> now.minusMonths(1);
            case "ALL" -> null;
            default -> now.minusWeeks(1);
        };
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
