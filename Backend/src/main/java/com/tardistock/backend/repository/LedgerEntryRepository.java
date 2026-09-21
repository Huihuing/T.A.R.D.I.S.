package com.tardistock.backend.repository;

import com.tardistock.backend.entity.LedgerEntry;
import com.tardistock.backend.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findTop100ByMemberOrderByCreatedAtDesc(Member member);
}
