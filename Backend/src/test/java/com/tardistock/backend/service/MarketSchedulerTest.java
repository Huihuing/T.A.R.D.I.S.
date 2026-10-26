package com.tardistock.backend.service;

import com.tardistock.backend.entity.PriceAlert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MarketSchedulerTest {

    @Test
    void limitOrderSchedulerContinuesAfterOneSymbolFails() {
        LimitOrderService orders = mock(LimitOrderService.class);
        FinnhubPriceService prices = mock(FinnhubPriceService.class);

        when(orders.pendingSymbolsAfter("", 40)).thenReturn(List.of("AAPL", "MSFT"));
        when(prices.getTradingPrice("AAPL")).thenReturn(100.0);
        when(prices.getTradingPrice("MSFT")).thenReturn(200.0);
        when(orders.processSymbol(eq("AAPL"), any(DoubleSupplier.class)))
                .thenThrow(new IllegalStateException("db failure"));

        LimitOrderScheduler scheduler =
                new LimitOrderScheduler(orders, prices);

        scheduler.processPendingOrders();

        verify(orders).processSymbol(eq("AAPL"), any(DoubleSupplier.class));
        verify(orders).processSymbol(eq("MSFT"), any(DoubleSupplier.class));
    }

    @Test
    void nextRunReachesSymbolsBeyondFirstFortyEvenWhenPricesAreUnavailable() {
        LimitOrderService orders = mock(LimitOrderService.class);
        FinnhubPriceService prices = mock(FinnhubPriceService.class);
        List<String> firstPage = IntStream.range(0, 40)
                .mapToObj(i -> String.format("S%02d", i)).toList();
        when(orders.pendingSymbolsAfter("", 40)).thenReturn(firstPage);
        when(orders.pendingSymbolsAfter("S39", 40)).thenReturn(List.of("S40", "S41"));
        when(orders.pendingSymbolsAfter("", 38)).thenReturn(firstPage.subList(0, 38));

        LimitOrderScheduler scheduler = new LimitOrderScheduler(orders, prices);
        scheduler.processPendingOrders();
        scheduler.processPendingOrders();

        verify(prices).getTradingPrice("S40");
        verify(prices).getTradingPrice("S41");
        verify(prices, times(80)).getTradingPrice(anyString());
        verify(orders, never()).processSymbol(anyString(), any(DoubleSupplier.class));
    }

    @Test
    void wrappedSmallSymbolSetIsNotScannedTwiceInOneRun() {
        LimitOrderService orders = mock(LimitOrderService.class);
        FinnhubPriceService prices = mock(FinnhubPriceService.class);
        when(orders.pendingSymbolsAfter("", 40)).thenReturn(List.of("AAPL", "MSFT"));
        when(orders.pendingSymbolsAfter("MSFT", 40)).thenReturn(List.of());

        LimitOrderScheduler scheduler = new LimitOrderScheduler(orders, prices);
        scheduler.processPendingOrders();
        scheduler.processPendingOrders();

        verify(prices, times(2)).getTradingPrice("AAPL");
        verify(prices, times(2)).getTradingPrice("MSFT");
        verify(prices, never()).getPrice(anyString());
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
