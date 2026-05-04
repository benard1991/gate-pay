package com.gatepay.kycservice.repository;


import com.gatepay.kycservice.model.KycAuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KycAuditTrailRepository extends JpaRepository<KycAuditTrail, Long> {

    Page<KycAuditTrail> findByKycIdOrderByPerformedAtDesc(Long kycId, Pageable pageable);

}
