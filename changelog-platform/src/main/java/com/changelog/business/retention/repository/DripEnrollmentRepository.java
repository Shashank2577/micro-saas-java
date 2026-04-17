package com.changelog.business.retention.repository;

import com.changelog.business.retention.model.DripEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DripEnrollmentRepository extends JpaRepository<DripEnrollment, UUID> {

    List<DripEnrollment> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<DripEnrollment> findByCustomerIdAndCampaignId(UUID customerId, UUID campaignId);

    List<DripEnrollment> findByStatusAndNextStepAtBefore(String status, LocalDateTime cutoff);
}
