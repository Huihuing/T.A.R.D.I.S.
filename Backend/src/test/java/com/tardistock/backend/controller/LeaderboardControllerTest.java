package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.WalletRepository;
import com.tardistock.backend.service.FinnhubPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class LeaderboardControllerTest {

    @Test
    void buildsLeaderboardWithoutPerMemberRepositoryQueries() {
        MemberRepository members = mock(MemberRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        PortfolioRepository portfolios = mock(PortfolioRepository.class);
        FinnhubPriceService prices = mock(FinnhubPriceService.class);

        Member first = mock(Member.class);
        Member second = mock(Member.class);
        when(first.getId()).thenReturn(1L);
        when(first.getUsername()).thenReturn("alpha");
        when(first.getNickname()).thenReturn("Alpha");
        when(second.getId()).thenReturn(2L);
        when(second.getUsername()).thenReturn("beta");
        when(second.getNickname()).thenReturn("Beta");

        Wallet firstWallet = mock(Wallet.class);
        Wallet secondWallet = mock(Wallet.class);
        when(firstWallet.getMember()).thenReturn(first);
        when(firstWallet.getBalance()).thenReturn(11_000.0);
        when(secondWallet.getMember()).thenReturn(second);
        when(secondWallet.getBalance()).thenReturn(9_000.0);

        Portfolio firstHolding = mock(Portfolio.class);
        Portfolio secondHolding = mock(Portfolio.class);
        when(firstHolding.getMember()).thenReturn(first);
        when(firstHolding.getSymbol()).thenReturn("AAPL");
        when(firstHolding.getAmount()).thenReturn(1);
        when(firstHolding.getAveragePrice()).thenReturn(100.0);
        when(secondHolding.getMember()).thenReturn(second);
        when(secondHolding.getSymbol()).thenReturn("AAPL");
        when(secondHolding.getAmount()).thenReturn(1);
        when(secondHolding.getAveragePrice()).thenReturn(100.0);

        when(members.findAll()).thenReturn(List.of(first, second));
        when(wallets.findAll()).thenReturn(
                List.of(firstWallet, secondWallet)
        );
        when(portfolios.findAll()).thenReturn(
                List.of(firstHolding, secondHolding)
        );
        when(prices.getPrice("AAPL")).thenReturn(200.0);

        LeaderboardController controller =
                new LeaderboardController(
                        members,
                        wallets,
                        portfolios,
                        prices
                );

        ResponseEntity<?> response = controller.getLeaderboard();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body =
                (List<Map<String, Object>>) response.getBody();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, body.size());
        assertEquals("alpha", body.get(0).get("username"));
        assertEquals(1, body.get(0).get("rank"));
        assertEquals(2, body.get(1).get("rank"));

        verify(members, times(1)).findAll();
        verify(wallets, times(1)).findAll();
        verify(portfolios, times(1)).findAll();
        verify(prices, times(1)).getPrice("AAPL");
        verifyNoMoreInteractions(prices);
    }
}
