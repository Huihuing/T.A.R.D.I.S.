package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.PriceAlert;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PriceAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class PriceAlertService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern SYMBOL_PATTERN =
            Pattern.compile("^[A-Z0-9.\\-]{1,20}$");
    private static final int MAX_ACTIVE_ALERTS = 20;

    private final PriceAlertRepository priceAlertRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    public PriceAlertService(
            PriceAlertRepository priceAlertRepository,
            MemberRepository memberRepository,
            NotificationService notificationService) {
        this.priceAlertRepository = priceAlertRepository;
        this.memberRepository = memberRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<PriceAlert> list(String username) {
        return priceAlertRepository.findByMemberOrderByCreatedAtDesc(
                requireMember(username)
        );
    }

    @Transactional
    public PriceAlert create(
            String username,
            String rawSymbol,
            String rawDirection,
            double targetPrice) {

        Member member = requireMember(username);

        if (priceAlertRepository.countByMemberAndActiveTrue(member)
                >= MAX_ACTIVE_ALERTS) {
            throw new IllegalStateException(
                    "활성 가격 알림은 최대 "
                            + MAX_ACTIVE_ALERTS + "개까지 등록할 수 있습니다."
            );
        }

        String symbol = normalizeSymbol(rawSymbol);
        String direction = normalizeDirection(rawDirection);

        if (!Double.isFinite(targetPrice) || targetPrice <= 0) {
            throw new IllegalArgumentException(
                    "목표 가격은 0보다 큰 숫자여야 합니다."
            );
        }

        return priceAlertRepository.save(new PriceAlert(
                member,
                symbol,
                direction,
                roundMoney(targetPrice),
                LocalDateTime.now(KST)
        ));
    }

    @Transactional
    public void delete(String username, Long id) {
        Member member = requireMember(username);
        PriceAlert alert = priceAlertRepository
                .findByIdAndMember(id, member)
                .orElseThrow(() -> new IllegalArgumentException(
                        "가격 알림을 찾을 수 없습니다."
                ));
        priceAlertRepository.delete(alert);
    }

    @Transactional
    public int triggerMatching(String symbol, double currentPrice) {
        if (!Double.isFinite(currentPrice) || currentPrice <= 0) {
            return 0;
        }

        List<PriceAlert> alerts =
                priceAlertRepository.findByActiveTrueAndSymbol(symbol);

        int triggered = 0;
        LocalDateTime now = LocalDateTime.now(KST);

        for (PriceAlert alert : alerts) {
            boolean reached =
                    ("ABOVE".equals(alert.getDirection())
                            && currentPrice >= alert.getTargetPrice())
                    || ("BELOW".equals(alert.getDirection())
                            && currentPrice <= alert.getTargetPrice());

            if (!reached) continue;

            alert.trigger(now);
            priceAlertRepository.save(alert);

            String condition = "ABOVE".equals(alert.getDirection())
                    ? "이상"
                    : "이하";

            notificationService.create(
                    alert.getMember(),
                    "PRICE_ALERT",
                    alert.getSymbol()
                            + " 현재가 $"
                            + String.format(Locale.ROOT, "%.2f", currentPrice)
                            + " — 설정한 $"
                            + String.format(
                                    Locale.ROOT,
                                    "%.2f",
                                    alert.getTargetPrice()
                            )
                            + " " + condition + " 조건에 도달했습니다."
            );
            triggered++;
        }

        return triggered;
    }

    @Transactional(readOnly = true)
    public List<PriceAlert> activeBatch() {
        return priceAlertRepository
                .findTop500ByActiveTrueOrderByCreatedAtAsc();
    }

    private Member requireMember(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "사용자를 찾을 수 없습니다."
                ));
    }

    private String normalizeSymbol(String raw) {
        String symbol = raw == null
                ? ""
                : raw.trim().toUpperCase(Locale.ROOT);

        if (!SYMBOL_PATTERN.matcher(symbol).matches()) {
            throw new IllegalArgumentException(
                    "종목 코드가 올바르지 않습니다."
            );
        }
        return symbol;
    }

    private String normalizeDirection(String raw) {
        String direction = raw == null
                ? ""
                : raw.trim().toUpperCase(Locale.ROOT);
        if (!"ABOVE".equals(direction) && !"BELOW".equals(direction)) {
            throw new IllegalArgumentException(
                    "가격 조건은 ABOVE 또는 BELOW여야 합니다."
            );
        }
        return direction;
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
