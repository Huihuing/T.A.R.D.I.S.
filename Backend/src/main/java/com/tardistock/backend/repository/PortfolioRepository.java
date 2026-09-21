package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByMember(Member member);

    Optional<Portfolio> findByMemberAndSymbol(Member member, String symbol);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Portfolio p where p.member = :member and p.symbol = :symbol")
    Optional<Portfolio> findForUpdateByMemberAndSymbol(
            @Param("member") Member member,
            @Param("symbol") String symbol);
}
