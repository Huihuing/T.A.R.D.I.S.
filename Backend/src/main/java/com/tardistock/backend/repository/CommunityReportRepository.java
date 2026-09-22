package com.tardistock.backend.repository;

import com.tardistock.backend.entity.CommunityReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityReportRepository
        extends JpaRepository<CommunityReport, Long> {

    List<CommunityReport> findAllByOrderByCreatedAtDesc();

    List<CommunityReport> findByStatusOrderByCreatedAtDesc(String status);

    List<CommunityReport> findByTargetTypeAndTargetIdAndStatus(
            String targetType,
            Long targetId,
            String status
    );

    boolean existsByTargetTypeAndTargetIdAndStatusAndReporterUsername(
            String targetType,
            Long targetId,
            String status,
            String reporterUsername
    );

    long countByStatus(String status);
}
