package com.tardistock.backend.repository;

import com.tardistock.backend.entity.MutationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MutationRequestRepository extends JpaRepository<MutationRequest, Long> {
    Optional<MutationRequest> findByUsernameAndRequestKey(String username, String requestKey);
}
