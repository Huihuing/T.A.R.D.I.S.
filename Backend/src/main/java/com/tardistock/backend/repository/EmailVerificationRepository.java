package com.tardistock.backend.repository;

import com.tardistock.backend.entity.EmailVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationRepository
        extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select verification
            from EmailVerification verification
            where verification.email = :email
            """)
    Optional<EmailVerification> findByEmailForUpdate(
            @Param("email") String email
    );
}
