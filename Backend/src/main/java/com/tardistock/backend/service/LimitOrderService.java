package com.tardistock.backend.service;

import com.tardistock.backend.entity.*;
import com.tardistock.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.regex.Pattern;

import static com.tardistock.backend.util.MoneyMath.fitsCents;
import static com.tardistock.backend.util.MoneyMath.roundCents;

@Service
public class LimitOrderService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern SYMBOL_PATTERN =
            Pattern.compile("^[A-Z0-9.\\-]{1,20}$");
    private static final int MAX_PENDING_ORDERS = 30;

    private final LimitOrderRepository limitOrderRepository;
    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;

    public LimitOrderService(
            LimitOrderRepository limitOrderRepository,
            MemberRepository memberRepository,
            WalletRepository walletRepository,
            PortfolioRepository portfolioRepository,
            TradeHistoryRepository tradeHistoryRepository,
            LedgerService ledgerService,
            NotificationService notificationService) {
        this.limitOrderRepository = limitOrderRepository;
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.portfolioRepository = portfolioRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.ledgerService = ledgerService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<LimitOrder> list(String username) {
        Member member = requireMember(username);
        return limitOrderRepository
                .findTop100ByMemberOrderByCreatedAtDesc(member);
    }

    @Transactional
    public LimitOrder create(
            String username,
            String rawSide,
            String rawSymbol,
            int amount,
            double limitPrice) {

        Member member = memberRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (limitOrderRepository.countByMemberAndStatus(
                member,
                "PENDING"
        ) >= MAX_PENDING_ORDERS) {
            throw new IllegalStateException(
                    "대기 중인 지정가 주문은 최대 "
                            + MAX_PENDING_ORDERS + "개까지 등록할 수 있습니다."
            );
        }

        String side = normalizeSide(rawSide);
        String symbol = normalizeSymbol(rawSymbol);

        if (amount <= 0 || amount > 1_000_000) {
            throw new IllegalArgumentException(
                    "주문 수량이 올바르지 않습니다."
            );
        }
        if (!Double.isFinite(limitPrice) || limitPrice <= 0) {
            throw new IllegalArgumentException(
                    "지정가는 0보다 큰 숫자여야 합니다."
            );
        }

        double normalizedLimitPrice = roundCents(limitPrice);
        if (!LimitOrder.isPersistablePrice(normalizedLimitPrice)) {
            throw new IllegalArgumentException("주문 가격은 DECIMAL(19,2) 범위의 유한한 숫자여야 합니다.");
        }
        if (normalizedLimitPrice < 0.01) {
            throw new IllegalArgumentException(
                    "지정가는 $0.01 이상이어야 합니다."
            );
        }

        if ("SELL".equals(side)) {
            int owned = portfolioRepository
                    .findByMemberAndSymbol(member, symbol)
                    .map(Portfolio::getAmount)
                    .orElse(0);
            if (owned < amount) {
                throw new IllegalArgumentException(
                        "현재 보유 수량보다 많은 매도 주문을 등록할 수 없습니다."
                );
            }
        }

        return limitOrderRepository.save(new LimitOrder(
                member,
                side,
                symbol,
                amount,
                normalizedLimitPrice,
                LocalDateTime.now(KST)
        ));
    }

    @Transactional
    public void cancel(String username, Long orderId) {
        Member member = requireMember(username);
        LimitOrder order = limitOrderRepository
                .findForUpdateByIdAndMember(orderId, member)
                .orElseThrow(() -> new IllegalArgumentException(
                        "주문을 찾을 수 없습니다."
                ));

        if (!order.isPending()) {
            throw new IllegalStateException(
                    "대기 중인 주문만 취소할 수 있습니다."
            );
        }

        order.cancel(LocalDateTime.now(KST));
        limitOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<String> pendingSymbolsAfter(String afterSymbol, int limit) {
        return limitOrderRepository.findPendingSymbolsAfter(
                afterSymbol, PageRequest.of(0, limit));
    }

    @Transactional
    public int processSymbol(String symbol, double marketPrice) {
        return processSymbol(symbol, () -> marketPrice);
    }

    @Transactional
    public int processSymbol(String symbol, DoubleSupplier currentPrice) {
        double marketPrice = currentPrice.getAsDouble();
        if (!Double.isFinite(marketPrice)
                || marketPrice <= 0
                || !TradeHistory.isPersistablePrice(marketPrice)) {
            return 0;
        }

        List<LimitOrder> orders =
                limitOrderRepository.findPendingForUpdateBySymbol(symbol);

        int processed = 0;
        for (LimitOrder order : orders) {
            if (!order.isPending()) continue;
            boolean shouldFill =
                    ("BUY".equals(order.getSide())
                            && marketPrice <= order.getLimitPrice())
                    || ("SELL".equals(order.getSide())
                            && marketPrice >= order.getLimitPrice());

            if (!shouldFill) continue;

            if (execute(order, currentPrice)) processed++;
        }
        return processed;
    }

    private boolean execute(LimitOrder order, DoubleSupplier currentPrice) {
        Member member = memberRepository
                .findByUsernameForUpdate(order.getMember().getUsername())
                .orElse(null);

        if (member == null) {
            reject(order, "사용자 정보를 찾을 수 없습니다.");
            return true;
        }

        Wallet wallet = walletRepository
                .findForUpdateByMember(member)
                .orElse(null);

        if (wallet == null) {
            reject(order, "지갑 정보를 찾을 수 없습니다.");
            return true;
        }

        Portfolio portfolio = portfolioRepository.findForUpdateByMemberAndSymbol(
                member, order.getSymbol()).orElse(null);
        // DB lock 대기 중 시세가 만료될 수 있으므로 실제 mutation 직전에 재검증합니다.
        double marketPrice = currentPrice.getAsDouble();
        if (!Double.isFinite(marketPrice) || marketPrice <= 0
                || !TradeHistory.isPersistablePrice(marketPrice)
                || ("BUY".equals(order.getSide()) && marketPrice > order.getLimitPrice())
                || ("SELL".equals(order.getSide()) && marketPrice < order.getLimitPrice())) {
            return false;
        }

        if ("BUY".equals(order.getSide())) {
            executeBuy(order, member, wallet, portfolio, marketPrice);
        } else {
            executeSell(order, member, wallet, portfolio, marketPrice);
        }
        return true;
    }

    private void executeBuy(
            LimitOrder order,
            Member member,
            Wallet wallet,
            Portfolio existingPortfolio,
            double marketPrice) {

        BigDecimal totalCost = roundCents(BigDecimal.valueOf(marketPrice)
                .multiply(BigDecimal.valueOf(order.getAmount())));
        if (!fitsCents(totalCost) || totalCost.signum() <= 0) {
            reject(
                    order,
                    "체결 금액이 허용 범위를 초과하여 주문이 취소되었습니다."
            );
            return;
        }
        if (wallet.getBalanceAmount().compareTo(totalCost) < 0) {
            reject(order, "체결 시점의 잔액이 부족하여 주문이 취소되었습니다.");
            return;
        }

        Portfolio portfolio = existingPortfolio == null ? new Portfolio(
                        member,
                        order.getSymbol(),
                        0,
                        0.0
                ) : existingPortfolio;

        BigDecimal newBalance = roundCents(wallet.getBalanceAmount().subtract(totalCost));
        if (!fitsCents(newBalance)) {
            reject(
                    order,
                    "체결 후 잔액이 허용 범위를 초과하여 주문이 취소되었습니다."
            );
            return;
        }

        long combinedAmount = (long) portfolio.getAmount() + order.getAmount();
        if (portfolio.getAmount() < 0 || combinedAmount > Integer.MAX_VALUE) {
            reject(order, "보유 수량이 허용 범위를 초과하여 주문이 취소되었습니다.");
            return;
        }
        BigDecimal newTotalValue = portfolio.getAveragePriceAmount()
                .multiply(BigDecimal.valueOf(portfolio.getAmount()))
                .add(BigDecimal.valueOf(marketPrice).multiply(BigDecimal.valueOf(order.getAmount())));
        int newAmount =
                (int) combinedAmount;
        BigDecimal newAveragePrice = newTotalValue.divide(
                BigDecimal.valueOf(newAmount), 6, RoundingMode.HALF_UP);
        if (newAveragePrice.precision() > 19 || newAveragePrice.signum() < 0) {
            reject(
                    order,
                    "평균 매입가가 허용 범위를 초과하여 주문이 취소되었습니다."
            );
            return;
        }

        wallet.setBalanceAmount(newBalance);
        walletRepository.save(wallet);

        portfolio.setAmount(newAmount);
        portfolio.setAveragePriceAmount(newAveragePrice);
        portfolioRepository.save(portfolio);

        recordFill(
                order,
                member,
                wallet,
                marketPrice,
                totalCost.negate(),
                "LIMIT_BUY"
        );
    }

    private void executeSell(
            LimitOrder order,
            Member member,
            Wallet wallet,
            Portfolio portfolio,
            double marketPrice) {

        if (portfolio == null
                || portfolio.getAmount() < order.getAmount()) {
            reject(order, "체결 시점의 보유 수량이 부족하여 주문이 취소되었습니다.");
            return;
        }

        BigDecimal proceeds = roundCents(BigDecimal.valueOf(marketPrice)
                .multiply(BigDecimal.valueOf(order.getAmount())));
        if (!fitsCents(proceeds) || proceeds.signum() <= 0) {
            reject(
                    order,
                    "체결 금액이 허용 범위를 초과하여 주문이 취소되었습니다."
            );
            return;
        }

        BigDecimal newBalance = roundCents(wallet.getBalanceAmount().add(proceeds));
        if (!fitsCents(newBalance)) {
            reject(
                    order,
                    "체결 후 잔액이 허용 범위를 초과하여 주문이 취소되었습니다."
            );
            return;
        }

        int newAmount =
                portfolio.getAmount() - order.getAmount();

        if (newAmount == 0) {
            portfolioRepository.delete(portfolio);
        } else {
            portfolio.setAmount(newAmount);
            portfolioRepository.save(portfolio);
        }

        wallet.setBalanceAmount(newBalance);
        walletRepository.save(wallet);

        recordFill(
                order,
                member,
                wallet,
                marketPrice,
                proceeds,
                "LIMIT_SELL"
        );
    }

    private void recordFill(
            LimitOrder order,
            Member member,
            Wallet wallet,
            double marketPrice,
            BigDecimal ledgerAmount,
            String ledgerType) {

        LocalDateTime now = LocalDateTime.now(KST);

        tradeHistoryRepository.save(new TradeHistory(
                member,
                order.getSide(),
                order.getSymbol(),
                order.getAmount(),
                marketPrice,
                now
        ));

        ledgerService.record(
                member,
                ledgerType,
                ledgerAmount,
                wallet.getBalanceAmount(),
                null,
                order.getSymbol(),
                order.getSymbol()
                        + " " + order.getAmount()
                        + "주 지정가 "
                        + ("BUY".equals(order.getSide())
                                ? "매수"
                                : "매도")
        );

        order.fill(roundCents(marketPrice), now);
        limitOrderRepository.save(order);

        notificationService.create(
                member,
                "LIMIT_ORDER",
                order.getSymbol()
                        + " "
                        + order.getAmount()
                        + "주 지정가 "
                        + ("BUY".equals(order.getSide())
                                ? "매수"
                                : "매도")
                        + " 주문이 $"
                        + String.format(
                                Locale.ROOT,
                                "%.2f",
                                marketPrice
                        )
                        + "에 체결되었습니다."
        );
    }

    private void reject(LimitOrder order, String message) {
        order.reject(message, LocalDateTime.now(KST));
        limitOrderRepository.save(order);

        notificationService.create(
                order.getMember(),
                "LIMIT_ORDER",
                order.getSymbol()
                        + " 지정가 주문이 처리되지 않았습니다. "
                        + message
        );
    }

    private Member requireMember(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "사용자를 찾을 수 없습니다."
                ));
    }

    private String normalizeSide(String raw) {
        String side = raw == null
                ? ""
                : raw.trim().toUpperCase(Locale.ROOT);
        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            throw new IllegalArgumentException(
                    "주문 구분은 BUY 또는 SELL이어야 합니다."
            );
        }
        return side;
    }

    private String normalizeSymbol(String raw) {
        String symbol = raw == null
                ? ""
                : raw.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL_PATTERN.matcher(symbol).matches()) {
            throw new IllegalArgumentException(
                    "종목 코드가 올바르지 않습니다."
            );
        }
        return symbol;
    }
}
