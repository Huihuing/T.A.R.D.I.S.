package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.TradeHistoryRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.service.EconomyService;
import com.tardistock.backend.service.FinnhubPriceService;
import com.tardistock.backend.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeControllerMoneyRangeTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private TradeHistoryRepository tradeHistoryRepository;
    @Mock
    private FinnhubPriceService finnhubPriceService;
    @Mock
    private EconomyService economyService;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private Authentication authentication;

    private TradeController controller;

    @BeforeEach
    void setUp() {
        controller = new TradeController(
                memberRepository,
                walletRepository,
                portfolioRepository,
                tradeHistoryRepository,
                finnhubPriceService,
                economyService,
                ledgerService
        );
    }

    @Test
    void buyRejectsTradeTotalOutsideDecimal19Scale2BeforeMutation() {
        Member member = new Member();
        Wallet wallet = new Wallet(member, 10_000_000_000_000_000d);
        authenticate("alice");
        when(finnhubPriceService.getPrice("AAPL"))
                .thenReturn(1_000_000_000_000d);
        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(walletRepository.findForUpdateByMember(member))
                .thenReturn(Optional.of(wallet));

        Map<String, String> response = controller.buyStock(
                Map.of("symbol", "AAPL", "amount", 1_000_000),
                authentication
        );

        assertEquals("FAIL", response.get("status"));
        assertEquals(
                "거래 금액이 허용 범위를 초과합니다.",
                response.get("message")
        );
        verify(walletRepository, never()).save(any());
        verify(portfolioRepository, never()).save(any());
        verify(portfolioRepository, never()).delete(any());
        verify(tradeHistoryRepository, never()).save(any());
        verifyLedgerNotRecorded();
    }

    @Test
    void sellRejectsTradeTotalOutsideDecimal19Scale2BeforeMutation() {
        Member member = new Member();
        Wallet wallet = new Wallet(member, 1_000.0);
        Portfolio portfolio = new Portfolio(
                member,
                "AAPL",
                1_000_000,
                100.0
        );
        authenticate("alice");
        when(finnhubPriceService.getPrice("AAPL"))
                .thenReturn(1_000_000_000_000d);
        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(walletRepository.findForUpdateByMember(member))
                .thenReturn(Optional.of(wallet));
        when(portfolioRepository.findForUpdateByMemberAndSymbol(
                member,
                "AAPL"
        )).thenReturn(Optional.of(portfolio));

        Map<String, String> response = controller.sellStock(
                Map.of("symbol", "AAPL", "amount", 1_000_000),
                authentication
        );

        assertEquals("FAIL", response.get("status"));
        assertEquals(
                "거래 금액이 허용 범위를 초과합니다.",
                response.get("message")
        );
        verify(walletRepository, never()).save(any());
        verify(portfolioRepository, never()).save(any());
        verify(portfolioRepository, never()).delete(any());
        verify(tradeHistoryRepository, never()).save(any());
        verifyLedgerNotRecorded();
    }

    @Test
    void sellRejectsPostTradeWalletOverflowBeforeMutation() {
        Member member = new Member();
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
        authenticate("alice");
        when(finnhubPriceService.getPrice("AAPL"))
                .thenReturn(10_000_000_000d);
        when(memberRepository.findByUsernameForUpdate("alice"))
                .thenReturn(Optional.of(member));
        when(walletRepository.findForUpdateByMember(member))
                .thenReturn(Optional.of(wallet));
        when(portfolioRepository.findForUpdateByMemberAndSymbol(
                member,
                "AAPL"
        )).thenReturn(Optional.of(portfolio));

        Map<String, String> response = controller.sellStock(
                Map.of("symbol", "AAPL", "amount", 1_000_000),
                authentication
        );

        assertEquals("FAIL", response.get("status"));
        assertEquals(
                "거래 후 잔액이 허용 범위를 초과합니다.",
                response.get("message")
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
        verify(tradeHistoryRepository, never()).save(any());
        verifyLedgerNotRecorded();
    }

    private void authenticate(String username) {
        when(authentication.getName()).thenReturn(username);
    }

    private void verifyLedgerNotRecorded() {
        verify(ledgerService, never()).record(
                any(Member.class),
                anyString(),
                anyDouble(),
                anyDouble(),
                any(),
                any(),
                anyString()
        );
    }
}
