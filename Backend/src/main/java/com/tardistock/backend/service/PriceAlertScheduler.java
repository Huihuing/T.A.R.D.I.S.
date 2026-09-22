package com.tardistock.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tardistock.backend.entity.PriceAlert;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class PriceAlertScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(PriceAlertScheduler.class);

    private static final int MAX_SYMBOLS_PER_RUN = 40;

    private final PriceAlertService priceAlertService;
    private final FinnhubPriceService finnhubPriceService;

    public PriceAlertScheduler(
            PriceAlertService priceAlertService,
            FinnhubPriceService finnhubPriceService) {
        this.priceAlertService = priceAlertService;
        this.finnhubPriceService = finnhubPriceService;
    }

    @Scheduled(
            fixedDelayString = "${price-alert.check-ms:300000}",
            initialDelayString = "${price-alert.initial-delay-ms:60000}"
    )
    public void checkAlerts() {
        List<PriceAlert> alerts = priceAlertService.activeBatch();
        if (alerts.isEmpty()) return;

        Set<String> symbols = new LinkedHashSet<>();
        for (PriceAlert alert : alerts) {
            symbols.add(alert.getSymbol());
            if (symbols.size() >= MAX_SYMBOLS_PER_RUN) {
                break;
            }
        }

        for (String symbol : symbols) {
            try {
                double price = finnhubPriceService.getPrice(symbol);
                if (Double.isFinite(price) && price > 0) {
                    priceAlertService.triggerMatching(symbol, price);
                }
            } catch (Exception e) {
                log.warn(
                        "Price-alert batch failed for {}: {}",
                        symbol,
                        e.getClass().getSimpleName()
                );
            }
        }
    }
}
