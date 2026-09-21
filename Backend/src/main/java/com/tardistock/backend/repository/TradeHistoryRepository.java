package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeHistoryRepository
        extends JpaRepository<TradeHistory, Long> {

    List<TradeHistory> findByMember(Member member);

    long countByMember(Member member);
}
