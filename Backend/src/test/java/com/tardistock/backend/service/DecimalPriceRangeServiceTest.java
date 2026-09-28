package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.LimitOrderRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.PriceAlertRepository;
import com.tardistock.backend.repository.TradeHistoryRepository;
import com.tardistock.backend.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecimalPriceRangeServiceTest {

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
    @Mock
    private PriceAlertRepository priceAlertRepository;

    @Test
    void limitOrderRejectsPriceOutsideDecimal19Scale2BeforeSave() {
        Member member = member();
        when(memberRepository.findByUsername("alice"))
                .thenReturn(Optional.of(member));
        when(limitOrderRepository.countByMemberAndStatus(member, "PENDING"))
                .thenReturn(0L);

        LimitOrderService service = new LimitOrderService(
                limitOrderRepository,
                memberRepository,
                walletRepository,
                portfolioRepository,
                tradeHistoryRepository,
                ledgerService,
                notificationService
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        "alice",
                        "BUY",
                        "AAPL",
                        1,
                        100_000_000_000_000_000d
                )
        );

        assertEquals(
                "주문 가격은 DECIMAL(19,2) 범위의 유한한 숫자여야 합니다.",
                error.getMessage()
        );
        verify(limitOrderRepository, never()).save(any());
    }

    @Test
    void priceAlertRejectsPriceOutsideDecimal19Scale2BeforeSave() {
        Member member = member();
        when(memberRepository.findByUsername("alice"))
                .thenReturn(Optional.of(member));
        when(priceAlertRepository.countByMemberAndActiveTrue(member))
                .thenReturn(0L);

        PriceAlertService service = new PriceAlertService(
                priceAlertRepository,
                memberRepository,
                notificationService
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        "alice",
                        "AAPL",
                        "ABOVE",
                        100_000_000_000_000_000d
                )
        );

        assertEquals(
                "목표 가격은 DECIMAL(19,2) 범위의 유한한 숫자여야 합니다.",
                error.getMessage()
        );
        verify(priceAlertRepository, never()).save(any());
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
