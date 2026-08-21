package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // 아이디(username)로 유저를 찾는 마법의 메서드!
    Optional<Member> findByUsername(String username);
}