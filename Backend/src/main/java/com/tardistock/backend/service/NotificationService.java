package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Notification;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.NotificationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(
            NotificationRepository notificationRepository,
            MemberRepository memberRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Notification create(
            Member member,
            String type,
            String message) {
        Notification notification = notificationRepository.save(
                new Notification(
                        member,
                        normalizeType(type),
                        normalizeMessage(message),
                        LocalDateTime.now(KST)
                )
        );

        messagingTemplate.convertAndSend(
                "/topic/alerts/" + member.getUsername(),
                (Object) Map.of(
                        "id", notification.getId(),
                        "type", notification.getType(),
                        "message", notification.getMessage(),
                        "createdAt",
                        notification.getCreatedAt().toString(),
                        "read", false
                )
        );
        return notification;
    }

    @Transactional(readOnly = true)
    public List<Notification> recent(String username) {
        Member member = requireMember(username);
        return notificationRepository
                .findTop50ByMemberOrderByCreatedAtDesc(member);
    }

    @Transactional(readOnly = true)
    public List<Notification> recentSecurity(String username) {
        Member member = requireMember(username);
        return notificationRepository
                .findTop20ByMemberAndTypeOrderByCreatedAtDesc(
                        member,
                        "SECURITY"
                );
    }

    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        return notificationRepository
                .countByMemberAndReadAtIsNull(
                        requireMember(username));
    }

    @Transactional
    public void markRead(String username, Long id) {
        Member member = requireMember(username);
        Notification notification = notificationRepository
                .findByIdAndMember(id, member)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "알림을 찾을 수 없습니다."));

        if (!notification.isRead()) {
            notification.setReadAt(LocalDateTime.now(KST));
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public int markAllRead(String username) {
        Member member = requireMember(username);
        List<Notification> unread =
                notificationRepository
                        .findByMemberAndReadAtIsNull(member);

        LocalDateTime now = LocalDateTime.now(KST);
        unread.forEach(notification ->
                notification.setReadAt(now));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    private Member requireMember(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다."));
    }

    private String normalizeType(String type) {
        String value = type == null ? "GENERAL" : type.trim();
        if (value.isEmpty()) return "GENERAL";
        return value.length() <= 40
                ? value
                : value.substring(0, 40);
    }

    private String normalizeMessage(String message) {
        String value = message == null ? "" : message.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "알림 메시지가 비어 있습니다.");
        }
        return value.length() <= 500
                ? value
                : value.substring(0, 500);
    }
}
