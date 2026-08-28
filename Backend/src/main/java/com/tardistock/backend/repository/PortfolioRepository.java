package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    // 📜 특정 유저의 모든 주식 찾기
    List<Portfolio> findByMember(Member member);
    // 📜 특정 유저의 특정 주식(예: AAPL) 찾기
    Optional<Portfolio> findByMemberAndSymbol(Member member, String symbol);
}