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
import com.tardistock.backend.service.MutationRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tardistock.backend.util.MoneyMath.fitsCents;
import static com.tardistock.backend.util.MoneyMath.roundCents;

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
    private MutationRequestService mutationRequests;

    @Autowired
    public void setMutationRequestService(MutationRequestService mutationRequests) {
        this.mutationRequests = mutationRequests;
    }

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
                .map(tradeHistoryRepository::findTop500ByMemberOrderByTradeTimeDesc)
                .orElseGet(List::of);
    }

    @GetMapping("/portfolio")
    public List<Portfolio> getPortfolio(Authentication authentication) {
        return authenticatedMember(authentication)
                .map(portfolioRepository::findByMember)
                .orElseGet(List::of);
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buyRequest(@RequestBody Map<String, Object> payload,
            Authentication authentication,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return mutationRequests.execute(authenticatedUsername(authentication), key, "STOCK_BUY",
                tradePayload(payload), () -> ResponseEntity.ok(buyStock(payload, authentication)));
    }

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

        double price = finnhubPriceService.getTradingPrice(symbol);
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

        Portfolio portfolio = portfolioRepository.findForUpdateByMemberAndSymbol(member, symbol)
                .orElse(new Portfolio(member, symbol, 0, 0.0));

        // Re-check after waiting for locks; a previously fresh quote may have expired.
        price = finnhubPriceService.getTradingPrice(symbol);
        if (!Double.isFinite(price) || price <= 0 || !TradeHistory.isPersistablePrice(price)) {
            return fail(response, "현재 시세를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
        BigDecimal totalCost = roundCents(BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(amount)));
        if (!fitsCents(totalCost) || totalCost.signum() <= 0) {
            return fail(response, "거래 금액이 허용 범위를 초과합니다.");
        }
        if (wallet.getBalanceAmount().compareTo(totalCost) < 0) {
            return fail(response, "잔액이 부족합니다!");
        }

        BigDecimal newBalance = wallet.getBalanceAmount().subtract(totalCost);
        if (!fitsCents(newBalance)) {
            return fail(response, "거래 후 잔액이 허용 범위를 초과합니다.");
        }

        long totalAmount = (long) portfolio.getAmount() + amount;
        if (portfolio.getAmount() < 0 || totalAmount > Integer.MAX_VALUE) {
            return fail(response, "보유 수량이 허용 범위를 초과합니다.");
        }
        int newAmount = (int) totalAmount;
        BigDecimal newTotalValue = portfolio.getAveragePriceAmount()
                .multiply(BigDecimal.valueOf(portfolio.getAmount()))
                .add(BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(amount)));
        BigDecimal newAveragePrice = newTotalValue.divide(BigDecimal.valueOf(newAmount), 6, RoundingMode.HALF_UP);
        if (newAveragePrice.precision() > 19 || newAveragePrice.signum() < 0) {
            return fail(response, "평균 매입가가 허용 범위를 초과합니다.");
        }

        wallet.setBalanceAmount(newBalance);
        walletRepository.save(wallet);

        portfolio.setAmount(newAmount);
        portfolio.setAveragePriceAmount(newAveragePrice);
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
                totalCost.negate(),
                wallet.getBalanceAmount(),
                null,
                symbol,
                symbol + " " + amount + "주 매수"
        );

        response.put("status", "SUCCESS");
        response.put("message", symbol + " " + amount + "주 매수 완료!");
        return response;
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sellRequest(@RequestBody Map<String, Object> payload,
            Authentication authentication,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return mutationRequests.execute(authenticatedUsername(authentication), key, "STOCK_SELL",
                tradePayload(payload), () -> ResponseEntity.ok(sellStock(payload, authentication)));
    }

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

        double price = finnhubPriceService.getTradingPrice(symbol);
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

        price = finnhubPriceService.getTradingPrice(symbol);
        if (!Double.isFinite(price) || price <= 0 || !TradeHistory.isPersistablePrice(price)) {
            return fail(response, "현재 시세를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
        BigDecimal proceeds = roundCents(BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(amount)));
        if (!fitsCents(proceeds) || proceeds.signum() <= 0) {
            return fail(response, "거래 금액이 허용 범위를 초과합니다.");
        }

        BigDecimal newBalance = wallet.getBalanceAmount().add(proceeds);
        if (!fitsCents(newBalance)) {
            return fail(response, "거래 후 잔액이 허용 범위를 초과합니다.");
        }

        Portfolio portfolio = portfolioOpt.get();
        int newAmount = portfolio.getAmount() - amount;
        if (newAmount == 0) {
            portfolioRepository.delete(portfolio);
        } else {
            portfolio.setAmount(newAmount);
            portfolioRepository.save(portfolio);
        }

        wallet.setBalanceAmount(newBalance);
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
                wallet.getBalanceAmount(),
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
        String symbol = value.toString().trim().toUpperCase(Locale.ROOT);
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

    private Map<String, Object> tradePayload(Map<String, Object> payload) {
        return Map.of("symbol", String.valueOf(normalizeSymbol(payload.get("symbol"))),
                "amount", parsePositiveAmount(payload.get("amount")));
    }
}
