package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceAlertRepository
        extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findTop200ByMemberOrderByCreatedAtDesc(Member member);

    Optional<PriceAlert> findByIdAndMember(Long id, Member member);

    long countByMemberAndActiveTrue(Member member);

    List<PriceAlert> findTop500ByActiveTrueOrderByCreatedAtAsc();

    List<PriceAlert> findByActiveTrueAndSymbol(String symbol);
}
