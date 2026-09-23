package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.service.FinnhubPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private static final double INITIAL_BALANCE = 10_000.0;
    private static final long LEADERBOARD_CACHE_MS = 30_000L;

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PortfolioRepository portfolioRepository;
    private final FinnhubPriceService finnhubPriceService;
    private final ReentrantLock leaderboardRefreshLock = new ReentrantLock();
    private volatile CacheEntry leaderboardCache;

    public LeaderboardController(
            MemberRepository memberRepository,
            WalletRepository walletRepository,
            PortfolioRepository portfolioRepository,
            FinnhubPriceService finnhubPriceService) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.portfolioRepository = portfolioRepository;
        this.finnhubPriceService = finnhubPriceService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getLeaderboard() {
        CacheEntry cached = leaderboardCache;
        if (isFresh(cached)) {
            return ResponseEntity.ok(cached.value());
        }

        if (!leaderboardRefreshLock.tryLock()) {
            if (cached != null) {
                return ResponseEntity.ok(cached.value());
            }
            leaderboardRefreshLock.lock();
        }

        try {
            cached = leaderboardCache;
            if (isFresh(cached)) {
                return ResponseEntity.ok(cached.value());
            }

            List<Map<String, Object>> result = buildLeaderboard();
            List<Map<String, Object>> immutableResult = List.copyOf(result);
            leaderboardCache = new CacheEntry(
                    immutableResult,
                    System.currentTimeMillis() + LEADERBOARD_CACHE_MS
            );

            return ResponseEntity.ok(immutableResult);
        } finally {
            leaderboardRefreshLock.unlock();
        }
    }

    private List<Map<String, Object>> buildLeaderboard() {
        List<Member> members = memberRepository.findAll();

        Map<Long, Wallet> walletsByMemberId =
                walletRepository.findAll().stream()
                        .collect(Collectors.toMap(
                                wallet -> wallet.getMember().getId(),
                                Function.identity(),
                                (first, ignored) -> first
                        ));

        Map<Long, List<Portfolio>> portfoliosByMemberId =
                portfolioRepository.findAll().stream()
                        .collect(Collectors.groupingBy(
                                portfolio ->
                                        portfolio.getMember().getId()
                        ));

        Map<String, Double> pricesBySymbol = new HashMap<>();
        List<Map<String, Object>> rankings =
                new ArrayList<>(members.size());

        for (Member member : members) {
            Wallet wallet = walletsByMemberId.get(member.getId());
            double cash = wallet == null ? 0.0 : wallet.getBalance();

            double portfolioValue = 0.0;
            for (Portfolio portfolio :
                    portfoliosByMemberId.getOrDefault(
                            member.getId(),
                            List.of()
                    )) {
                double currentPrice =
                        pricesBySymbol.computeIfAbsent(
                                portfolio.getSymbol(),
                                finnhubPriceService::getPrice
                        );
                double priceToUse =
                        currentPrice > 0
                                ? currentPrice
                                : portfolio.getAveragePrice();

                portfolioValue +=
                        portfolio.getAmount() * priceToUse;
            }

            double totalAsset = cash + portfolioValue;
            double roi =
                    ((totalAsset - INITIAL_BALANCE)
                            / INITIAL_BALANCE) * 100.0;

            rankings.add(Map.of(
                    "username", member.getUsername(),
                    "nickname", member.getNickname(),
                    "totalAsset", totalAsset,
                    "roi", roi
            ));
        }

        rankings.sort((a, b) ->
                Double.compare(
                        (Double) b.get("totalAsset"),
                        (Double) a.get("totalAsset")
                ));

        List<Map<String, Object>> result = new ArrayList<>();
        int limit = Math.min(rankings.size(), 10);

        for (int i = 0; i < limit; i++) {
            Map<String, Object> ranking = rankings.get(i);
            result.add(Map.of(
                    "rank", i + 1,
                    "username", ranking.get("username"),
                    "nickname", ranking.get("nickname"),
                    "totalAsset", ranking.get("totalAsset"),
                    "roi", ranking.get("roi")
            ));
        }

        return result;
    }

    private boolean isFresh(CacheEntry cached) {
        return cached != null
                && cached.expiresAt() > System.currentTimeMillis();
    }

    private record CacheEntry(
            List<Map<String, Object>> value,
            long expiresAt) {}
}
