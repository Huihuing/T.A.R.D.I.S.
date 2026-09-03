package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.UserEconomy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserEconomyRepository extends JpaRepository<UserEconomy, Long> {
    Optional<UserEconomy> findByMember(Member member);
}
