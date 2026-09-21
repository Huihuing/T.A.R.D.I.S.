package com.tardistock.backend.service;

import com.tardistock.backend.entity.LimitOrder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class LimitOrderScheduler {

    private static final int MAX_SYMBOLS_PER_RUN = 40;

    private final LimitOrderService limitOrderService;
    private final FinnhubPriceService finnhubPriceService;

    public LimitOrderScheduler(
            LimitOrderService limitOrderService,
            FinnhubPriceService finnhubPriceService) {
        this.limitOrderService = limitOrderService;
        this.finnhubPriceService = finnhubPriceService;
    }

    @Scheduled(
            fixedDelayString = "${limit-order.check-ms:300000}",
            initialDelayString = "${limit-order.initial-delay-ms:90000}"
    )
    public void processPendingOrders() {
        List<LimitOrder> pending =
                limitOrderService.pendingBatch();
        if (pending.isEmpty()) return;

        Set<String> symbols = new LinkedHashSet<>();
        for (LimitOrder order : pending) {
            symbols.add(order.getSymbol());
            if (symbols.size() >= MAX_SYMBOLS_PER_RUN) {
                break;
            }
        }

        for (String symbol : symbols) {
            double price = finnhubPriceService.getPrice(symbol);
            if (price > 0) {
                limitOrderService.processSymbol(symbol, price);
            }
        }
    }
}
