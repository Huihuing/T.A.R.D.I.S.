package com.tardistock.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class LimitOrderScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(LimitOrderScheduler.class);

    private static final int MAX_SYMBOLS_PER_RUN = 40;

    private final LimitOrderService limitOrderService;
    private final FinnhubPriceService finnhubPriceService;
    private String lastSymbol = "";

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
    public synchronized void processPendingOrders() {
        Set<String> symbols = new LinkedHashSet<>(
                limitOrderService.pendingSymbolsAfter(lastSymbol, MAX_SYMBOLS_PER_RUN));
        if (symbols.size() < MAX_SYMBOLS_PER_RUN && !lastSymbol.isEmpty()) {
            symbols.addAll(limitOrderService.pendingSymbolsAfter(
                    "", MAX_SYMBOLS_PER_RUN - symbols.size()));
        }

        int processed = 0;
        int unavailable = 0;
        for (String symbol : symbols) {
            // 장애나 미체결 주문도 cursor를 진행시켜 후순위 종목이 굶지 않게 합니다.
            lastSymbol = symbol;
            try {
                double price = finnhubPriceService.getTradingPrice(symbol);
                if (Double.isFinite(price) && price > 0) {
                    processed += limitOrderService.processSymbol(
                            symbol, () -> finnhubPriceService.getTradingPrice(symbol));
                } else {
                    unavailable++;
                }
            } catch (Exception e) {
                log.warn(
                        "Limit-order batch failed for {}: {}",
                        symbol,
                        e.getClass().getSimpleName()
                );
            }
        }
        if (!symbols.isEmpty()) {
            log.info("Limit-order scan: symbols={}, processed={}, unavailable={}",
                    symbols.size(), processed, unavailable);
        }
    }
}
