package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.CommentRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.PostRepository;
import com.tardistock.backend.repository.TradeHistoryRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.service.FinnhubPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final double INITIAL_BALANCE = 10_000.0;

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final FinnhubPriceService finnhubPriceService;

    public ProfileController(
            MemberRepository memberRepository,
            WalletRepository walletRepository,
            PortfolioRepository portfolioRepository,
            TradeHistoryRepository tradeHistoryRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            FinnhubPriceService finnhubPriceService) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.portfolioRepository = portfolioRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.finnhubPriceService = finnhubPriceService;
    }

    @GetMapping("/{username}")
    public ResponseEntity<?> getPublicProfile(
            @PathVariable String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank() || normalized.length() > 20) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "올바른 사용자 아이디가 아닙니다."));
        }

        Member member = memberRepository.findByUsername(normalized)
                .orElse(null);
        if (member == null) {
            return ResponseEntity.notFound().build();
        }

        Wallet wallet = walletRepository.findByMember(member).orElse(null);
        double cash = wallet == null ? 0.0 : wallet.getBalance();

        List<Portfolio> holdings = portfolioRepository.findByMember(member);
        double portfolioValue = 0.0;
        for (Portfolio holding : holdings) {
            double current = finnhubPriceService.getPrice(
                    holding.getSymbol()
            );
            double price = current > 0
                    ? current
                    : holding.getAveragePrice();
            portfolioValue += holding.getAmount() * price;
        }

        double totalAsset = cash + portfolioValue;
        double roi = ((totalAsset - INITIAL_BALANCE)
                / INITIAL_BALANCE) * 100.0;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("username", member.getUsername());
        response.put("nickname", member.getNickname());
        response.put("totalAsset", totalAsset);
        response.put("roi", roi);
        response.put(
                "tradeCount",
                tradeHistoryRepository.countByMember(member)
        );
        response.put("postCount", postRepository.countByMember(member));
        response.put(
                "commentCount",
                commentRepository.countByMember(member)
        );

        return ResponseEntity.ok(response);
    }
}
