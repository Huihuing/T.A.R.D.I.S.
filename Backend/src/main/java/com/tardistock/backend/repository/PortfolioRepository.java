package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    // 주식 이름(symbol)으로 내가 보유한 주식을 DB에서 찾아오는 마법의 명령어입니다.
    Optional<Portfolio> findBySymbol(String symbol);
}