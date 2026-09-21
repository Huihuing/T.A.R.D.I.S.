package com.tardistock.backend.repository;

import com.tardistock.backend.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetCodeRepository
        extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findByEmailIgnoreCase(String email);
}
