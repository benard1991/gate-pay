package com.gatepay.kycservice.repository;

import com.gatepay.kycservice.enums.KycStatus;
import com.gatepay.kycservice.model.KycRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KycRepository extends JpaRepository<KycRequest, Long> {
    Optional<KycRequest> findByUserId(Long userId);

    Optional<KycRequest> findTopByUserIdAndStatusOrderByCreatedAtDesc(Long userId, KycStatus status);

    boolean existsByUserIdAndStatus(Long userId, KycStatus status);

    Optional<KycRequest> findByIdAndStatus(Long id, KycStatus status);

    boolean existsByNin(String nin);
    boolean existsByBvn(String bvn);

}
