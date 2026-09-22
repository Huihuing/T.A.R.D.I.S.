package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByMemberOrderByCreatedAtDesc(Member member);

    long deleteByTokenHash(String tokenHash);

    long deleteByMemberAndTokenHashNot(
            Member member,
            String tokenHash
    );

    long deleteByExpiresAtBefore(LocalDateTime cutoff);

    long deleteByMember(Member member);
}
