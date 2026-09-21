package com.tardistock.backend.repository;

import com.tardistock.backend.entity.LimitOrder;
import com.tardistock.backend.entity.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LimitOrderRepository
        extends JpaRepository<LimitOrder, Long> {

    List<LimitOrder> findTop100ByMemberOrderByCreatedAtDesc(Member member);

    long countByMemberAndStatus(Member member, String status);

    List<LimitOrder> findTop500ByStatusOrderByCreatedAtAsc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from LimitOrder o
            where o.symbol = :symbol
              and o.status = 'PENDING'
            order by o.createdAt asc
            """)
    List<LimitOrder> findPendingForUpdateBySymbol(
            @Param("symbol") String symbol
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from LimitOrder o
            where o.id = :id
              and o.member = :member
            """)
    Optional<LimitOrder> findForUpdateByIdAndMember(
            @Param("id") Long id,
            @Param("member") Member member
    );
}
