package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByMember(Member member);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.member = :member")
    Optional<Wallet> findForUpdateByMember(@Param("member") Member member);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w
            from Wallet w
            join fetch w.member m
            where m.username in :usernames
            order by m.username
            """)
    List<Wallet> findAllForUpdateByUsernames(@Param("usernames") Collection<String> usernames);
}
