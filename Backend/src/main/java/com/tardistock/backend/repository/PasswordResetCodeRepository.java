package com.tardistock.backend.repository;

import com.tardistock.backend.entity.PasswordResetCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetCodeRepository
        extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reset
            from PasswordResetCode reset
            where reset.email = :email
            """)
    Optional<PasswordResetCode> findByEmailForUpdate(
            @Param("email") String email
    );
}
