package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Notification;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    @Test
    void recentSecurityReturnsOnlySecurityEvents() {
        NotificationRepository notifications =
                mock(NotificationRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        SimpMessagingTemplate messaging =
                mock(SimpMessagingTemplate.class);

        Member member = new Member(
                "alice",
                "pw",
                "Alice",
                "alice@example.test",
                "pin"
        );

        Notification security = new Notification(
                member,
                "SECURITY",
                "계정 비밀번호가 변경되었습니다.",
                LocalDateTime.of(2026, 9, 22, 11, 0)
        );

        when(members.findByUsername("alice"))
                .thenReturn(Optional.of(member));
        when(notifications
                .findTop20ByMemberAndTypeOrderByCreatedAtDesc(
                        member,
                        "SECURITY"
                ))
                .thenReturn(List.of(security));

        NotificationService service = new NotificationService(
                notifications,
                members,
                messaging
        );

        List<Notification> result =
                service.recentSecurity("alice");

        assertEquals(1, result.size());
        assertEquals("SECURITY", result.get(0).getType());
        assertEquals(
                "계정 비밀번호가 변경되었습니다.",
                result.get(0).getMessage()
        );

        verify(notifications)
                .findTop20ByMemberAndTypeOrderByCreatedAtDesc(
                        member,
                        "SECURITY"
                );
    }
}
