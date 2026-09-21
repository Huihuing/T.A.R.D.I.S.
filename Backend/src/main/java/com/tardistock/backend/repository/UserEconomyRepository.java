package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.UserEconomy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserEconomyRepository extends JpaRepository<UserEconomy, Long> {
    Optional<UserEconomy> findByMember(Member member);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from UserEconomy e where e.member = :member")
    Optional<UserEconomy> findForUpdateByMember(@Param("member") Member member);
}
