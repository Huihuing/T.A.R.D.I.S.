package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotRepository
        extends JpaRepository<PortfolioSnapshot, Long> {

    Optional<PortfolioSnapshot>
    findTopByMemberOrderByCapturedAtDesc(Member member);

    List<PortfolioSnapshot>
    findTop1000ByMemberOrderByCapturedAtDesc(Member member);

    long deleteByMemberAndCapturedAtBefore(
            Member member,
            LocalDateTime cutoff
    );
}
