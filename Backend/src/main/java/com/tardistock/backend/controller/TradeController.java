package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import com.tardistock.backend.entity.TradeHistory;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.TradeHistoryRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.service.EconomyService;
import com.tardistock.backend.service.FinnhubPriceService;
import com.tardistock.backend.service.LedgerService;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/trade")
public class TradeController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final FinnhubPriceService finnhubPriceService;
    private final EconomyService economyService;
    private final LedgerService ledgerService;

    public TradeController(
            MemberRepository memberRepository,
            WalletRepository walletRepository,
            PortfolioRepository portfolioRepository,
            TradeHistoryRepository tradeHistoryRepository,
            FinnhubPriceService finnhubPriceService,
            EconomyService economyService,
            LedgerService ledgerService) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.portfolioRepository = portfolioRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.finnhubPriceService = finnhubPriceService;
        this.economyService = economyService;
        this.ledgerService = ledgerService;
    }

    @GetMapping("/balance")
    public double getBalance(Authentication authentication) {
        return authenticatedMember(authentication)
                .flatMap(walletRepository::findByMember)
                .map(Wallet::getBalance)
                .orElse(0.0);
    }

    @GetMapping("/history")
    public List<TradeHistory> getHistory(Authentication authentication) {
        return authenticatedMember(authentication)
                .map(tradeHistoryRepository::findByMember)
                .orElseGet(List::of);
    }

    @GetMapping("/portfolio")
    public List<Portfolio> getPortfolio(Authentication authentication) {
        return authenticatedMember(authentication)
                .map(portfolioRepository::findByMember)
                .orElseGet(List::of);
    }

    @PostMapping("/buy")
    @Transactional
    public Map<String, String> buyStock(
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        Map<String, String> response = new HashMap<>();
        String username = authenticatedUsername(authentication);
        if (username == null) {
            return fail(response, "로그인이 필요합니다.");
        }

        String symbol = normalizeSymbol(payload.get("symbol"));
        int amount = parsePositiveAmount(payload.get("amount"));
        if (symbol == null || amount <= 0) {
            return fail(response, "종목과 수량을 올바르게 입력해주세요.");
        }

        double price = finnhubPriceService.getPrice(symbol);
        if (!Double.isFinite(price) || price <= 0) {
            return fail(response, "현재 시세를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        Member member = memberRepository.findByUsernameForUpdate(username).orElse(null);
        if (member == null) {
            return fail(response, "사용자를 찾을 수 없습니다.");
        }

        Wallet wallet = walletRepository.findForUpdateByMember(member).orElse(null);
        double totalCost = price * amount;
        if (!Double.isFinite(totalCost) || wallet == null || wallet.getBalance() < totalCost) {
            return fail(response, "잔액이 부족합니다!");
        }

        Portfolio portfolio = portfolioRepository.findForUpdateByMemberAndSymbol(member, symbol)
                .orElse(new Portfolio(member, symbol, 0, 0.0));

        wallet.setBalance(wallet.getBalance() - totalCost);
        walletRepository.save(wallet);

        double newTotalValue =
                (portfolio.getAmount() * portfolio.getAveragePrice()) + totalCost;
        int newAmount = portfolio.getAmount() + amount;
        portfolio.setAmount(newAmount);
        portfolio.setAveragePrice(newTotalValue / newAmount);
        portfolioRepository.save(portfolio);

        tradeHistoryRepository.save(new TradeHistory(
                member,
                "BUY",
                symbol,
                amount,
                price,
                LocalDateTime.now(KST)
        ));
        ledgerService.record(
                member,
                "STOCK_BUY",
                -totalCost,
                wallet.getBalance(),
                null,
                symbol,
                symbol + " " + amount + "주 매수"
        );

        response.put("status", "SUCCESS");
        response.put("message", symbol + " " + amount + "주 매수 완료!");
        return response;
    }

    @PostMapping("/sell")
    @Transactional
    public Map<String, String> sellStock(
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        Map<String, String> response = new HashMap<>();
        String username = authenticatedUsername(authentication);
        if (username == null) {
            return fail(response, "로그인이 필요합니다.");
        }

        String symbol = normalizeSymbol(payload.get("symbol"));
        int amount = parsePositiveAmount(payload.get("amount"));
        if (symbol == null || amount <= 0) {
            return fail(response, "종목과 수량을 올바르게 입력해주세요.");
        }

        double price = finnhubPriceService.getPrice(symbol);
        if (!Double.isFinite(price) || price <= 0) {
            return fail(response, "현재 시세를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        Member member = memberRepository.findByUsernameForUpdate(username).orElse(null);
        if (member == null) {
            return fail(response, "사용자를 찾을 수 없습니다.");
        }

        Wallet wallet = walletRepository.findForUpdateByMember(member).orElse(null);
        if (wallet == null) {
            return fail(response, "지갑을 찾을 수 없습니다.");
        }

        Optional<Portfolio> portfolioOpt =
                portfolioRepository.findForUpdateByMemberAndSymbol(member, symbol);
        if (portfolioOpt.isEmpty() || portfolioOpt.get().getAmount() < amount) {
            return fail(response, "보유 주식이 부족합니다!");
        }

        Portfolio portfolio = portfolioOpt.get();
        int newAmount = portfolio.getAmount() - amount;
        if (newAmount == 0) {
            portfolioRepository.delete(portfolio);
        } else {
            portfolio.setAmount(newAmount);
            portfolioRepository.save(portfolio);
        }

        double proceeds = price * amount;
        wallet.setBalance(wallet.getBalance() + proceeds);
        walletRepository.save(wallet);

        tradeHistoryRepository.save(new TradeHistory(
                member,
                "SELL",
                symbol,
                amount,
                price,
                LocalDateTime.now(KST)
        ));
        ledgerService.record(
                member,
                "STOCK_SELL",
                proceeds,
                wallet.getBalance(),
                null,
                symbol,
                symbol + " " + amount + "주 매도"
        );

        response.put("status", "SUCCESS");
        response.put("message", symbol + " " + amount + "주 매도 완료!");
        return response;
    }

    @PostMapping("/relief")
    public Map<String, Object> bankruptcyRelief(Authentication authentication) {
        String username = authenticatedUsername(authentication);
        if (username == null) {
            return Map.of("status", "FAIL", "message", "로그인이 필요합니다.");
        }
        return economyService.claimBankruptcyRelief(username, 1000.0);
    }

    private Optional<Member> authenticatedMember(Authentication authentication) {
        String username = authenticatedUsername(authentication);
        return username == null
                ? Optional.empty()
                : memberRepository.findByUsername(username);
    }

    private String authenticatedUsername(Authentication authentication) {
        if (authentication == null
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return authentication.getName();
    }

    private String normalizeSymbol(Object value) {
        if (value == null) return null;
        String symbol = value.toString().trim().toUpperCase();
        return symbol.matches("[A-Z0-9.\\-]{1,12}") ? symbol : null;
    }

    private int parsePositiveAmount(Object value) {
        if (value == null) return -1;
        try {
            int amount = Integer.parseInt(value.toString());
            return amount > 0 && amount <= 1_000_000 ? amount : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Map<String, String> fail(Map<String, String> response, String message) {
        response.put("status", "FAIL");
        response.put("message", message);
        return response;
    }
}
