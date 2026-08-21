package com.tardistock.backend.repository;

import com.tardistock.backend.entity.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

// DB에 저장하고 꺼내오는 모든 쿼리문을 JpaRepository가 알아서 다 만들어줍니다!
public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {
}