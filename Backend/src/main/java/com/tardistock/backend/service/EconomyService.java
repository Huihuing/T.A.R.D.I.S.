package com.tardistock.backend.service;

import com.tardistock.backend.entity.*;
import com.tardistock.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class EconomyService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final UserEconomyRepository userEconomyRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final LedgerService ledgerService;

    public EconomyService(MemberRepository memberRepository,
                          WalletRepository walletRepository,
                          UserEconomyRepository userEconomyRepository,
                          TradeHistoryRepository tradeHistoryRepository,
                          PostRepository postRepository,
                          CommentRepository commentRepository,
                          LedgerService ledgerService) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.userEconomyRepository = userEconomyRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.ledgerService = ledgerService;
    }

    public UserEconomy getOrCreateEconomy(Member member) {
        return userEconomyRepository.findByMember(member).orElseGet(() ->
                userEconomyRepository.save(new UserEconomy(member)));
    }

    private UserEconomy getOrCreateEconomyForUpdate(Member member) {
        return userEconomyRepository.findForUpdateByMember(member).orElseGet(() ->
                userEconomyRepository.save(new UserEconomy(member)));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStatus(String username) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Wallet wallet = walletRepository.findByMember(member)
                .orElseThrow(() -> new IllegalArgumentException("지갑 정보를 찾을 수 없습니다."));
        UserEconomy economy = userEconomyRepository.findByMember(member)
                .orElse(new UserEconomy(member));

        LocalDate today = LocalDate.now(KST);
        boolean canCheckIn =
                economy.getLastCheckInDate() == null
                        || !economy.getLastCheckInDate().equals(today);

        int streak = economy.getAttendanceStreak();
        if (economy.getLastCheckInDate() != null
                && !economy.getLastCheckInDate().equals(today)
                && !economy.getLastCheckInDate().equals(today.minusDays(1))) {
            streak = 0;
        }

        double balance = wallet.getBalance();
        boolean balanceLow = balance < 100.0;
        LocalDateTime now = LocalDateTime.now(KST);
        boolean cooldownPassed =
                economy.getLastBankruptcyClaim() == null
                        || ChronoUnit.HOURS.between(
                                economy.getLastBankruptcyClaim(), now) >= 24;
        boolean canClaimBankruptcy = balanceLow && cooldownPassed;

        long hoursRemaining = 0;
        if (economy.getLastBankruptcyClaim() != null && !cooldownPassed) {
            hoursRemaining = Math.max(
                    0,
                    24 - ChronoUnit.HOURS.between(
                            economy.getLastBankruptcyClaim(), now)
            );
        }

        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime nextDayStart = today.plusDays(1).atStartOfDay();

        boolean hasTradeToday =
                tradeHistoryRepository
                        .existsByMemberAndTradeTypeIgnoreCaseAndTradeTimeGreaterThanEqualAndTradeTimeLessThan(
                                member,
                                "BUY",
                                dayStart,
                                nextDayStart
                        );
        boolean tradeQuestClaimed =
                today.equals(economy.getTradeQuestClaimedDate());

        boolean hasPostToday =
                postRepository
                        .existsByMemberAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                member,
                                dayStart,
                                nextDayStart
                        )
                        || commentRepository
                        .existsByMemberAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                member,
                                dayStart,
                                nextDayStart
                        );
        boolean postQuestClaimed =
                today.equals(economy.getPostQuestClaimedDate());

        Map<String, Object> result = new HashMap<>();
        result.put("username", username);
        result.put("balance", balance);
        result.put("canCheckIn", canCheckIn);
        result.put("attendanceStreak", streak);
        result.put("lastCheckInDate", economy.getLastCheckInDate());
        result.put("canClaimBankruptcy", canClaimBankruptcy);
        result.put("bankruptcyBalanceLow", balanceLow);
        result.put("bankruptcyHoursRemaining", hoursRemaining);

        Map<String, Object> quests = new HashMap<>();
        quests.put("checkIn",
                Map.of("completed", !canCheckIn, "reward", 500));
        quests.put("trade",
                Map.of("completed", hasTradeToday,
                        "claimed", tradeQuestClaimed,
                        "reward", 300));
        quests.put("community",
                Map.of("completed", hasPostToday,
                        "claimed", postQuestClaimed,
                        "reward", 200));
        result.put("quests", quests);

        return result;
    }

    @Transactional
    public Map<String, Object> checkIn(String username) {
        Member member = memberRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Wallet wallet = walletRepository.findForUpdateByMember(member)
                .orElseThrow(() -> new IllegalArgumentException("지갑 정보를 찾을 수 없습니다."));
        UserEconomy economy = getOrCreateEconomyForUpdate(member);

        LocalDate today = LocalDate.now(KST);
        if (today.equals(economy.getLastCheckInDate())) {
            throw new IllegalStateException("오늘은 이미 출석 체크를 완료했습니다!");
        }

        int newStreak = 1;
        if (today.minusDays(1).equals(economy.getLastCheckInDate())) {
            newStreak = economy.getAttendanceStreak() + 1;
        }

        double reward = 500.0;
        String bonusMsg = "";
        if (newStreak >= 7) {
            reward += 1000.0;
            bonusMsg = " (7일 연속 출석 보너스 +$1,000!)";
        } else if (newStreak >= 3) {
            reward += 300.0;
            bonusMsg = " (3일 연속 출석 보너스 +$300!)";
        }

        economy.setAttendanceStreak(newStreak);
        economy.setLastCheckInDate(today);
        userEconomyRepository.save(economy);

        wallet.setBalance(wallet.getBalance() + reward);
        walletRepository.save(wallet);
        ledgerService.record(
                member,
                "CHECK_IN_REWARD",
                reward,
                wallet.getBalance(),
                "출석 체크 보상"
        );

        Map<String, Object> res = new HashMap<>();
        res.put("status", "SUCCESS");
        res.put("reward", reward);
        res.put("streak", newStreak);
        res.put("newBalance", wallet.getBalance());
        res.put("message", "출석 체크 완료! $" + String.format("%.0f", reward)
                + "가 지급되었습니다." + bonusMsg);
        return res;
    }

    @Transactional
    public Map<String, Object> claimBankruptcyRelief(
            String username,
            double rewardAmount) {
        Member member = memberRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Wallet wallet = walletRepository.findForUpdateByMember(member)
                .orElseThrow(() -> new IllegalArgumentException("지갑 정보를 찾을 수 없습니다."));
        UserEconomy economy = getOrCreateEconomyForUpdate(member);
        LocalDateTime now = LocalDateTime.now(KST);

        if (wallet.getBalance() >= 100.0) {
            throw new IllegalStateException(
                    "파산 구제 지원금은 보유 현금이 $100 미만일 때만 신청 가능합니다.");
        }

        if (economy.getLastBankruptcyClaim() != null
                && ChronoUnit.HOURS.between(
                        economy.getLastBankruptcyClaim(), now) < 24) {
            long remaining = Math.max(
                    1,
                    24 - ChronoUnit.HOURS.between(
                            economy.getLastBankruptcyClaim(), now)
            );
            throw new IllegalStateException(
                    "파산 지원금은 24시간마다 1회 지원됩니다. (남은 시간: "
                            + remaining + "시간)");
        }

        double validReward = Math.max(1000.0, Math.min(rewardAmount, 5000.0));

        economy.setLastBankruptcyClaim(now);
        userEconomyRepository.save(economy);

        wallet.setBalance(wallet.getBalance() + validReward);
        walletRepository.save(wallet);
        ledgerService.record(
                member,
                "BANKRUPTCY_RELIEF",
                validReward,
                wallet.getBalance(),
                "긴급 지원금"
        );

        Map<String, Object> res = new HashMap<>();
        res.put("status", "SUCCESS");
        res.put("reward", validReward);
        res.put("newBalance", wallet.getBalance());
        res.put("message", "🎉 파산 구제 룰렛 당첨! 긴급 지원금 $"
                + String.format("%.0f", validReward)
                + "가 충전되었습니다.");
        return res;
    }

    @Transactional
    public Map<String, Object> claimQuest(String username, String questType) {
        Member member = memberRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Wallet wallet = walletRepository.findForUpdateByMember(member)
                .orElseThrow(() -> new IllegalArgumentException("지갑 정보를 찾을 수 없습니다."));
        UserEconomy economy = getOrCreateEconomyForUpdate(member);
        LocalDate today = LocalDate.now(KST);

        double reward;
        String questName;

        if ("TRADE".equalsIgnoreCase(questType)) {
            if (today.equals(economy.getTradeQuestClaimedDate())) {
                throw new IllegalStateException(
                        "오늘의 매수 미션 보상을 이미 수령하셨습니다.");
            }
            LocalDateTime dayStart = today.atStartOfDay();
            LocalDateTime nextDayStart =
                    today.plusDays(1).atStartOfDay();
            boolean hasTradeToday =
                    tradeHistoryRepository
                            .existsByMemberAndTradeTypeIgnoreCaseAndTradeTimeGreaterThanEqualAndTradeTimeLessThan(
                                    member,
                                    "BUY",
                                    dayStart,
                                    nextDayStart
                            );
            if (!hasTradeToday) {
                throw new IllegalStateException(
                        "오늘 주식 매수(BUY) 기록이 없습니다. 먼저 주식을 매수해 보세요!");
            }
            reward = 300.0;
            questName = "일일 매수 미션";
            economy.setTradeQuestClaimedDate(today);
        } else if ("COMMUNITY".equalsIgnoreCase(questType)) {
            if (today.equals(economy.getPostQuestClaimedDate())) {
                throw new IllegalStateException(
                        "오늘의 커뮤니티 미션 보상을 이미 수령하셨습니다.");
            }
            LocalDateTime dayStart = today.atStartOfDay();
            LocalDateTime nextDayStart =
                    today.plusDays(1).atStartOfDay();
            boolean hasPostToday =
                    postRepository
                            .existsByMemberAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                    member,
                                    dayStart,
                                    nextDayStart
                            )
                            || commentRepository
                            .existsByMemberAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                    member,
                                    dayStart,
                                    nextDayStart
                            );
            if (!hasPostToday) {
                throw new IllegalStateException(
                        "오늘 커뮤니티에 작성한 게시글이나 댓글이 없습니다. 커뮤니티에 참여해 보세요!");
            }
            reward = 200.0;
            questName = "커뮤니티 활동 미션";
            economy.setPostQuestClaimedDate(today);
        } else {
            throw new IllegalArgumentException("알 수 없는 퀘스트 타입입니다.");
        }

        userEconomyRepository.save(economy);
        wallet.setBalance(wallet.getBalance() + reward);
        walletRepository.save(wallet);
        ledgerService.record(
                member,
                "QUEST_REWARD",
                reward,
                wallet.getBalance(),
                questName + " 보상"
        );

        Map<String, Object> res = new HashMap<>();
        res.put("status", "SUCCESS");
        res.put("reward", reward);
        res.put("newBalance", wallet.getBalance());
        res.put("message", "🎯 " + questName + " 보상 $"
                + String.format("%.0f", reward)
                + " 수령 완료!");
        return res;
    }
}
