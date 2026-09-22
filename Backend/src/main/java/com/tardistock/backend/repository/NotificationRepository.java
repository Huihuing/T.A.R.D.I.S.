package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByMemberOrderByCreatedAtDesc(
            Member member);

    List<Notification> findTop20ByMemberAndTypeOrderByCreatedAtDesc(
            Member member,
            String type
    );

    long countByMemberAndReadAtIsNull(Member member);

    Optional<Notification> findByIdAndMember(
            Long id,
            Member member);

    List<Notification> findByMemberAndReadAtIsNull(Member member);
}
