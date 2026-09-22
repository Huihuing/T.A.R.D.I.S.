package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.PriceAlert;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PriceAlertRepository;
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
class PriceAlertServiceTest {

    @Mock
    private PriceAlertRepository priceAlertRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private NotificationService notificationService;

    private PriceAlertService service;
    private Member member;

    @BeforeEach
    void setUp() {
        service = new PriceAlertService(
                priceAlertRepository,
                memberRepository,
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
    void listUsesBoundedHistoryQuery() {
        when(memberRepository.findByUsername("alice"))
                .thenReturn(Optional.of(member));
        when(priceAlertRepository
                .findTop200ByMemberOrderByCreatedAtDesc(member))
                .thenReturn(List.of());

        service.list("alice");

        verify(priceAlertRepository)
                .findTop200ByMemberOrderByCreatedAtDesc(member);
        verify(priceAlertRepository, never())
                .findAll();
    }

    @Test
    void createsNormalizedAlert() {
        when(memberRepository.findByUsername("alice"))
                .thenReturn(Optional.of(member));
        when(priceAlertRepository.countByMemberAndActiveTrue(member))
                .thenReturn(0L);
        when(priceAlertRepository.save(any(PriceAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PriceAlert alert = service.create(
                "alice",
                " aapl ",
                "above",
                200.123
        );

        assertEquals("AAPL", alert.getSymbol());
        assertEquals("ABOVE", alert.getDirection());
        assertEquals(200.12, alert.getTargetPrice());
        assertTrue(alert.isActive());
    }

    @Test
    void aboveAlertTriggersOnceWhenPriceReached() {
        PriceAlert alert = new PriceAlert(
                member,
                "AAPL",
                "ABOVE",
                200.0,
                LocalDateTime.now()
        );

        when(priceAlertRepository.findByActiveTrueAndSymbol("AAPL"))
                .thenReturn(List.of(alert));

        int triggered = service.triggerMatching("AAPL", 201.50);

        assertEquals(1, triggered);
        assertFalse(alert.isActive());
        assertNotNull(alert.getTriggeredAt());
        verify(priceAlertRepository).save(alert);
        verify(notificationService).create(
                eq(member),
                eq("PRICE_ALERT"),
                contains("AAPL")
        );
    }

    @Test
    void belowAlertDoesNotTriggerBeforeCondition() {
        PriceAlert alert = new PriceAlert(
                member,
                "TSLA",
                "BELOW",
                150.0,
                LocalDateTime.now()
        );

        when(priceAlertRepository.findByActiveTrueAndSymbol("TSLA"))
                .thenReturn(List.of(alert));

        int triggered = service.triggerMatching("TSLA", 151.0);

        assertEquals(0, triggered);
        assertTrue(alert.isActive());
        verify(priceAlertRepository, never()).save(alert);
        verifyNoInteractions(notificationService);
    }
}
