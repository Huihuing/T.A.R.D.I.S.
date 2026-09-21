package com.tardistock.backend.service;

import com.tardistock.backend.entity.*;
import com.tardistock.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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

        Member member = requireMember(username);

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
                roundMoney(limitPrice),
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
    public List<LimitOrder> pendingBatch() {
        return limitOrderRepository
                .findTop500ByStatusOrderByCreatedAtAsc("PENDING");
    }

    @Transactional
    public int processSymbol(String symbol, double marketPrice) {
        if (!Double.isFinite(marketPrice) || marketPrice <= 0) {
            return 0;
        }

        List<LimitOrder> orders =
                limitOrderRepository.findPendingForUpdateBySymbol(symbol);

        int processed = 0;
        for (LimitOrder order : orders) {
            boolean shouldFill =
                    ("BUY".equals(order.getSide())
                            && marketPrice <= order.getLimitPrice())
                    || ("SELL".equals(order.getSide())
                            && marketPrice >= order.getLimitPrice());

            if (!shouldFill) continue;

            execute(order, marketPrice);
            processed++;
        }
        return processed;
    }

    private void execute(LimitOrder order, double marketPrice) {
        Member member = memberRepository
                .findByUsernameForUpdate(order.getMember().getUsername())
                .orElse(null);

        if (member == null) {
            reject(order, "사용자 정보를 찾을 수 없습니다.");
            return;
        }

        Wallet wallet = walletRepository
                .findForUpdateByMember(member)
                .orElse(null);

        if (wallet == null) {
            reject(order, "지갑 정보를 찾을 수 없습니다.");
            return;
        }

        if ("BUY".equals(order.getSide())) {
            executeBuy(order, member, wallet, marketPrice);
        } else {
            executeSell(order, member, wallet, marketPrice);
        }
    }

    private void executeBuy(
            LimitOrder order,
            Member member,
            Wallet wallet,
            double marketPrice) {

        double totalCost = marketPrice * order.getAmount();
        if (!Double.isFinite(totalCost)
                || wallet.getBalance() < totalCost) {
            reject(order, "체결 시점의 잔액이 부족하여 주문이 취소되었습니다.");
            return;
        }

        Portfolio portfolio = portfolioRepository
                .findForUpdateByMemberAndSymbol(
                        member,
                        order.getSymbol()
                )
                .orElse(new Portfolio(
                        member,
                        order.getSymbol(),
                        0,
                        0.0
                ));

        wallet.setBalance(wallet.getBalance() - totalCost);
        walletRepository.save(wallet);

        double newTotalValue =
                portfolio.getAmount() * portfolio.getAveragePrice()
                        + totalCost;
        int newAmount =
                portfolio.getAmount() + order.getAmount();

        portfolio.setAmount(newAmount);
        portfolio.setAveragePrice(newTotalValue / newAmount);
        portfolioRepository.save(portfolio);

        recordFill(
                order,
                member,
                wallet,
                marketPrice,
                -totalCost,
                "LIMIT_BUY"
        );
    }

    private void executeSell(
            LimitOrder order,
            Member member,
            Wallet wallet,
            double marketPrice) {

        Portfolio portfolio = portfolioRepository
                .findForUpdateByMemberAndSymbol(
                        member,
                        order.getSymbol()
                )
                .orElse(null);

        if (portfolio == null
                || portfolio.getAmount() < order.getAmount()) {
            reject(order, "체결 시점의 보유 수량이 부족하여 주문이 취소되었습니다.");
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

        double proceeds = marketPrice * order.getAmount();
        wallet.setBalance(wallet.getBalance() + proceeds);
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
            double ledgerAmount,
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
                wallet.getBalance(),
                null,
                order.getSymbol(),
                order.getSymbol()
                        + " " + order.getAmount()
                        + "주 지정가 "
                        + ("BUY".equals(order.getSide())
                                ? "매수"
                                : "매도")
        );

        order.fill(roundMoney(marketPrice), now);
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

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
