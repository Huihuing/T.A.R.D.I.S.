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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LimitOrderServiceTest {

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
    private Member member;

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

        member = new Member(
                "alice",
                "encoded",
                "Alice",
                "alice@example.test",
                "encoded-pin"
        );
    }

    @Test
    void buyOrderFillsWhenMarketIsAtOrBelowLimit() {
        LimitOrder order = new LimitOrder(
                member,
                "BUY",
                "AAPL",
                2,
                200.0,
                LocalDateTime.now()
        );
        Wallet wallet = new Wallet(member, 1000.0);

        when(limitOrderRepository.findPendingForUpdateBySymbol("AAPL"))
                .thenReturn(List.of(order));
        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(walletRepository.findForUpdateByMember(member))
                .thenReturn(Optional.of(wallet));
        when(portfolioRepository.findForUpdateByMemberAndSymbol(
                member,
                "AAPL"
        )).thenReturn(Optional.empty());

        int processed = service.processSymbol("AAPL", 190.0);

        assertEquals(1, processed);
        assertEquals("FILLED", order.getStatus());
        assertEquals(190.0, order.getFillPrice());
        assertEquals(620.0, wallet.getBalance());

        verify(tradeHistoryRepository).save(any());
        verify(ledgerService).record(
                eq(member),
                eq("LIMIT_BUY"),
                eq(-380.0),
                eq(620.0),
                isNull(),
                eq("AAPL"),
                contains("지정가 매수")
        );
        verify(notificationService).create(
                eq(member),
                eq("LIMIT_ORDER"),
                contains("체결")
        );
    }

    @Test
    void sellOrderWaitsWhileMarketIsBelowLimit() {
        LimitOrder order = new LimitOrder(
                member,
                "SELL",
                "TSLA",
                1,
                300.0,
                LocalDateTime.now()
        );

        when(limitOrderRepository.findPendingForUpdateBySymbol("TSLA"))
                .thenReturn(List.of(order));

        int processed = service.processSymbol("TSLA", 299.0);

        assertEquals(0, processed);
        assertEquals("PENDING", order.getStatus());
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
    void buyOrderIsRejectedWhenCashIsInsufficientAtExecution() {
        LimitOrder order = new LimitOrder(
                member,
                "BUY",
                "NVDA",
                10,
                100.0,
                LocalDateTime.now()
        );
        Wallet wallet = new Wallet(member, 50.0);

        when(limitOrderRepository.findPendingForUpdateBySymbol("NVDA"))
                .thenReturn(List.of(order));
        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(walletRepository.findForUpdateByMember(member))
                .thenReturn(Optional.of(wallet));

        int processed = service.processSymbol("NVDA", 90.0);

        assertEquals(1, processed);
        assertEquals("REJECTED", order.getStatus());
        assertTrue(order.getResultMessage().contains("잔액"));
        verify(notificationService).create(
                eq(member),
                eq("LIMIT_ORDER"),
                contains("처리되지 않았습니다")
        );
        verifyNoInteractions(tradeHistoryRepository, ledgerService);
    }

    @Test
    void pendingOrderCanBeCancelledByOwner() {
        LimitOrder order = new LimitOrder(
                member,
                "BUY",
                "AAPL",
                1,
                180.0,
                LocalDateTime.now()
        );

        when(memberRepository.findByUsername("alice"))
                .thenReturn(Optional.of(member));
        when(limitOrderRepository.findForUpdateByIdAndMember(
                1L,
                member
        )).thenReturn(Optional.of(order));

        service.cancel("alice", 1L);

        assertEquals("CANCELLED", order.getStatus());
        verify(limitOrderRepository).save(order);
    }
}
