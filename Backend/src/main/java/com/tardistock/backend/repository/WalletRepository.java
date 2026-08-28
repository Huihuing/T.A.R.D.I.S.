package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    // 📜 유저 정보로 그 유저의 지갑을 찾아오는 메서드
    Optional<Wallet> findByMember(Member member);
}