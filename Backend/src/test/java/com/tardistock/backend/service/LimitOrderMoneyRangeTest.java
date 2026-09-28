package com.tardistock.backend.service;

import com.tardistock.backend.entity.LimitOrder;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.LimitOrderRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.TradeHistoryRepository;
import com.tardistock.backend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LimitOrderMoneyRangeTest {

    @Mock
    private LimitOrderRepository limitOrderRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private TradeHistoryRepository tradeHistoryRepository;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private NotificationService notificationService;

    private LimitOrderService service;

    @BeforeEach
    void setUp() {
        service = new LimitOrderService(
                limitOrderRepository,
                memberRepository,
                walletRepository,
                portfolioRepository,
                tradeHistoryRepository,
                ledgerService,
                notificationService
        );
    }

    @Test
    void ignoresMarketPriceOutsideTradeHistoryDecimalRange() {
        int processed = service.processSymbol(
                "AAPL",
                10_000_000_000_000d
        );

        assertEquals(0, processed);
        verify(limitOrderRepository, never())
                .findPendingForUpdateBySymbol(any());
        verifyNoInteractions(
                memberRepository,
                walletRepository,
                portfolioRepository,
                tradeHistoryRepository,
                ledgerService,
                notificationService
        );
    }

    @Test
    void buyOverflowRejectsOrderBeforeWalletOrPortfolioMutation() {
        Member member = member();
        Wallet wallet = new Wallet(
                member,
                10_000_000_000_000_000d
        );
        LimitOrder order = new LimitOrder(
                member,
                "BUY",
                "AAPL",
                1_000_000,
                1_000_000_000_000d,
                LocalDateTime.now()
        );
        when(limitOrderRepository.findPendingForUpdateBySymbol("AAPL"))
                .thenReturn(List.of(order));
        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(walletRepository.findForUpdateByMember(member))
                .thenReturn(Optional.of(wallet));

        int processed = service.processSymbol(
                "AAPL",
                1_000_000_000_000d
        );

        assertEquals(1, processed);
        assertEquals("REJECTED", order.getStatus());
        assertEquals(
                "체결 금액이 허용 범위를 초과하여 주문이 취소되었습니다.",
                order.getResultMessage()
        );
        assertEquals(
                10_000_000_000_000_000d,
                wallet.getBalance(),
                0.0
        );
        verify(walletRepository, never()).save(any());
        verify(portfolioRepository, never()).save(any());
        verify(portfolioRepository, never()).delete(any());
        verifyNoInteractions(tradeHistoryRepository, ledgerService);
    }

    @Test
    void sellWalletOverflowRejectsBeforePortfolioMutation() {
        Member member = member();
        Wallet wallet = new Wallet(
                member,
                95_000_000_000_000_000d
        );
        Portfolio portfolio = new Portfolio(
                member,
                "AAPL",
                1_000_000,
                100.0
        );
        LimitOrder order = new LimitOrder(
                member,
                "SELL",
                "AAPL",
                1_000_000,
                1.0,
                LocalDateTime.now()
        );
        when(limitOrderRepository.findPendingForUpdateBySymbol("AAPL"))
                .thenReturn(List.of(order));
        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(walletRepository.findForUpdateByMember(member))
                .thenReturn(Optional.of(wallet));
        when(portfolioRepository.findForUpdateByMemberAndSymbol(
                member,
                "AAPL"
        )).thenReturn(Optional.of(portfolio));

        int processed = service.processSymbol(
                "AAPL",
                10_000_000_000d
        );

        assertEquals(1, processed);
        assertEquals("REJECTED", order.getStatus());
        assertEquals(
                "체결 후 잔액이 허용 범위를 초과하여 주문이 취소되었습니다.",
                order.getResultMessage()
        );
        assertEquals(1_000_000, portfolio.getAmount());
        assertEquals(
                95_000_000_000_000_000d,
                wallet.getBalance(),
                0.0
        );
        verify(walletRepository, never()).save(any());
        verify(portfolioRepository, never()).save(any());
        verify(portfolioRepository, never()).delete(any());
        verifyNoInteractions(tradeHistoryRepository, ledgerService);
    }

    private Member member() {
        return new Member(
                "alice",
                "encoded",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );
    }
}
