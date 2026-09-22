package com.tardistock.backend.service;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Notification;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    @Test
    void transactionalCreateWaitsUntilAfterCommit() {
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
        Notification saved = mock(Notification.class);
        when(saved.getId()).thenReturn(1L);
        when(saved.getMember()).thenReturn(member);
        when(saved.getType()).thenReturn("GENERAL");
        when(saved.getMessage()).thenReturn("hello");
        when(saved.getCreatedAt()).thenReturn(
                LocalDateTime.of(2026, 9, 22, 15, 0)
        );
        when(notifications.save(any(Notification.class)))
                .thenReturn(saved);

        NotificationService service = new NotificationService(
                notifications,
                members,
                messaging
        );

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager
                .setActualTransactionActive(true);
        try {
            service.create(member, "GENERAL", "hello");

            verifyNoInteractions(messaging);

            TransactionSynchronizationManager
                    .getSynchronizations()
                    .forEach(sync -> sync.afterCommit());

            verify(messaging).convertAndSend(
                    eq("/topic/alerts/alice"),
                    any(Object.class)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager
                    .setActualTransactionActive(false);
        }
    }

    @Test
    void createOutsideTransactionSendsRealtime() {
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
        Notification saved = mock(Notification.class);
        when(saved.getId()).thenReturn(1L);
        when(saved.getMember()).thenReturn(member);
        when(saved.getType()).thenReturn("GENERAL");
        when(saved.getMessage()).thenReturn("hello");
        when(saved.getCreatedAt()).thenReturn(
                LocalDateTime.of(2026, 9, 22, 15, 0)
        );
        when(notifications.save(any(Notification.class)))
                .thenReturn(saved);

        NotificationService service = new NotificationService(
                notifications,
                members,
                messaging
        );

        service.create(member, "GENERAL", "hello");

        verify(messaging).convertAndSend(
                eq("/topic/alerts/alice"),
                any(Object.class)
        );
    }

    @Test
    void realtimeFailureDoesNotFailNotificationCreate() {
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
        Notification saved = mock(Notification.class);
        when(saved.getId()).thenReturn(1L);
        when(saved.getMember()).thenReturn(member);
        when(saved.getType()).thenReturn("GENERAL");
        when(saved.getMessage()).thenReturn("hello");
        when(saved.getCreatedAt()).thenReturn(
                LocalDateTime.of(2026, 9, 22, 15, 0)
        );
        when(notifications.save(any(Notification.class)))
                .thenReturn(saved);
        doThrow(new RuntimeException("broker unavailable"))
                .when(messaging)
                .convertAndSend(anyString(), any(Object.class));

        NotificationService service = new NotificationService(
                notifications,
                members,
                messaging
        );

        assertDoesNotThrow(
                () -> service.create(member, "GENERAL", "hello")
        );
    }

    @Test
    void markAllReadUsesBulkUpdate() {
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

        when(members.findByUsername("alice"))
                .thenReturn(Optional.of(member));
        when(notifications.markAllReadByMember(
                eq(member),
                any(LocalDateTime.class)
        )).thenReturn(17);

        NotificationService service = new NotificationService(
                notifications,
                members,
                messaging
        );

        int updated = service.markAllRead("alice");

        assertEquals(17, updated);
        verify(notifications).markAllReadByMember(
                eq(member),
                any(LocalDateTime.class)
        );
        verify(notifications, never())
                .findByMemberAndReadAtIsNull(any());
        verify(notifications, never())
                .saveAll(any());
    }

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
