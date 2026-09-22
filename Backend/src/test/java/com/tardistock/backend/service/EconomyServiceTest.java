package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.UserEconomy;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.CommentRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PostRepository;
import com.tardistock.backend.repository.TradeHistoryRepository;
import com.tardistock.backend.repository.UserEconomyRepository;
import com.tardistock.backend.repository.WalletRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EconomyServiceTest {

    @Test
    void statusUsesExistsQueriesInsteadOfLoadingFullActivityLists() {
        MemberRepository members = mock(MemberRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        UserEconomyRepository economies =
                mock(UserEconomyRepository.class);
        TradeHistoryRepository trades =
                mock(TradeHistoryRepository.class);
        PostRepository posts = mock(PostRepository.class);
        CommentRepository comments = mock(CommentRepository.class);
        LedgerService ledger = mock(LedgerService.class);

        Member member = mock(Member.class);
        Wallet wallet = mock(Wallet.class);
        UserEconomy economy = mock(UserEconomy.class);

        when(members.findByUsername("alpha"))
                .thenReturn(Optional.of(member));
        when(wallets.findByMember(member))
                .thenReturn(Optional.of(wallet));
        when(economies.findByMember(member))
                .thenReturn(Optional.of(economy));
        when(wallet.getBalance()).thenReturn(5_000.0);
        when(economy.getAttendanceStreak()).thenReturn(2);
        when(economy.getLastCheckInDate())
                .thenReturn(LocalDate.now(
                        java.time.ZoneId.of("Asia/Seoul")
                ).minusDays(1));

        when(trades
                .existsByMemberAndTradeTypeIgnoreCaseAndTradeTimeGreaterThanEqualAndTradeTimeLessThan(
                        eq(member),
                        eq("BUY"),
                        any(),
                        any()
                ))
                .thenReturn(true);
        when(posts
                .existsByMemberAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        eq(member),
                        any(),
                        any()
                ))
                .thenReturn(false);
        when(comments
                .existsByMemberAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        eq(member),
                        any(),
                        any()
                ))
                .thenReturn(true);

        EconomyService service = new EconomyService(
                members,
                wallets,
                economies,
                trades,
                posts,
                comments,
                ledger
        );

        Map<String, Object> status = service.getStatus("alpha");

        @SuppressWarnings("unchecked")
        Map<String, Object> quests =
                (Map<String, Object>) status.get("quests");

        @SuppressWarnings("unchecked")
        Map<String, Object> tradeQuest =
                (Map<String, Object>) quests.get("trade");

        @SuppressWarnings("unchecked")
        Map<String, Object> communityQuest =
                (Map<String, Object>) quests.get("community");

        assertEquals(true, tradeQuest.get("completed"));
        assertEquals(true, communityQuest.get("completed"));

        verify(trades, never()).findTop500ByMemberOrderByTradeTimeDesc(any());
        verify(posts, never()).findByMember(any());
        verify(comments, never()).findByMember(any());
    }
}
