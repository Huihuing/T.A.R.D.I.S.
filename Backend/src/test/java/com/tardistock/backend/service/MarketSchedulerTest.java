package com.tardistock.backend.service;

import com.tardistock.backend.entity.LimitOrder;
import com.tardistock.backend.entity.PriceAlert;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class MarketSchedulerTest {

    @Test
    void limitOrderSchedulerContinuesAfterOneSymbolFails() {
        LimitOrderService orders = mock(LimitOrderService.class);
        FinnhubPriceService prices = mock(FinnhubPriceService.class);

        LimitOrder first = mock(LimitOrder.class);
        LimitOrder second = mock(LimitOrder.class);
        when(first.getSymbol()).thenReturn("AAPL");
        when(second.getSymbol()).thenReturn("MSFT");
        when(orders.pendingBatch()).thenReturn(List.of(first, second));
        when(prices.getPrice("AAPL")).thenReturn(100.0);
        when(prices.getPrice("MSFT")).thenReturn(200.0);
        when(orders.processSymbol("AAPL", 100.0))
                .thenThrow(new IllegalStateException("db failure"));

        LimitOrderScheduler scheduler =
                new LimitOrderScheduler(orders, prices);

        scheduler.processPendingOrders();

        verify(orders).processSymbol("AAPL", 100.0);
        verify(orders).processSymbol("MSFT", 200.0);
    }

    @Test
    void priceAlertSchedulerContinuesAfterOneSymbolFails() {
        PriceAlertService alerts = mock(PriceAlertService.class);
        FinnhubPriceService prices = mock(FinnhubPriceService.class);

        PriceAlert first = mock(PriceAlert.class);
        PriceAlert second = mock(PriceAlert.class);
        when(first.getSymbol()).thenReturn("TSLA");
        when(second.getSymbol()).thenReturn("NVDA");
        when(alerts.activeBatch()).thenReturn(List.of(first, second));
        when(prices.getPrice("TSLA")).thenReturn(300.0);
        when(prices.getPrice("NVDA")).thenReturn(400.0);
        doThrow(new IllegalStateException("db failure"))
                .when(alerts)
                .triggerMatching("TSLA", 300.0);

        PriceAlertScheduler scheduler =
                new PriceAlertScheduler(alerts, prices);

        scheduler.checkAlerts();

        verify(alerts).triggerMatching("TSLA", 300.0);
        verify(alerts).triggerMatching("NVDA", 400.0);
    }
}
