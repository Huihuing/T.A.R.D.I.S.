package com.tardistock.backend.repository;

import com.tardistock.backend.entity.EmailCodeSendQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailCodeSendQuotaRepository
        extends JpaRepository<EmailCodeSendQuota, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select quota
            from EmailCodeSendQuota quota
            where quota.email = :email
            """)
    Optional<EmailCodeSendQuota> findByEmailForUpdate(
            @Param("email") String email
    );
}
