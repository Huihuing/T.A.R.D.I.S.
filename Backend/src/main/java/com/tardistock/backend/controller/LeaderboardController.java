package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.service.FinnhubPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PortfolioRepository portfolioRepository;
    private final FinnhubPriceService finnhubPriceService;

    public LeaderboardController(MemberRepository memberRepository, WalletRepository walletRepository,
                                 PortfolioRepository portfolioRepository, FinnhubPriceService finnhubPriceService) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.portfolioRepository = portfolioRepository;
        this.finnhubPriceService = finnhubPriceService;
    }

    @GetMapping
    public ResponseEntity<?> getLeaderboard() {
        List<Member> members = memberRepository.findAll();
        List<Map<String, Object>> rankings = new ArrayList<>();

        // 가입 시 기본 지급되는 초기 자본금 (수익률 계산용)
        final double INITIAL_BALANCE = 10000.0;

        for (Member member : members) {
            Optional<Wallet> walletOpt = walletRepository.findByMember(member);
            double balance = walletOpt.map(Wallet::getBalance).orElse(0.0);

            List<Portfolio> portfolios = portfolioRepository.findByMember(member);
            double portfolioValue = 0.0;
            for (Portfolio p : portfolios) {
                // Finnhub 실시간 시세로 자산 가치 계산 (API 실패 시 평단가로 폴백)
                double currentPrice = finnhubPriceService.getPrice(p.getSymbol());
                double priceToUse = currentPrice > 0 ? currentPrice : p.getAveragePrice();
                portfolioValue += (p.getAmount() * priceToUse);
            }

            double totalAsset = balance + portfolioValue;
            double roi = ((totalAsset - INITIAL_BALANCE) / INITIAL_BALANCE) * 100.0;

            rankings.add(Map.of(
                "username", member.getUsername(),
                "nickname", member.getNickname() != null ? member.getNickname() : member.getUsername(),
                "totalAsset", totalAsset,
                "roi", roi
            ));
        }

        // 총 자산(totalAsset)을 기준으로 내림차순 정렬 (가장 돈이 많은 사람이 1등)
        rankings.sort((a, b) -> Double.compare((Double) b.get("totalAsset"), (Double) a.get("totalAsset")));

        // 등수(Rank) 부여 및 최종 응답 리스트 생성 (상위 10명만)
        List<Map<String, Object>> finalRankings = new ArrayList<>();
        int limit = Math.min(rankings.size(), 10);
        
        for (int i = 0; i < limit; i++) {
            Map<String, Object> r = rankings.get(i);
            finalRankings.add(Map.of(
                "rank", i + 1,
                "username", r.get("username"),
                "nickname", r.get("nickname"),
                "totalAsset", r.get("totalAsset"),
                "roi", r.get("roi")
            ));
        }

        return ResponseEntity.ok(finalRankings);
    }
}