package com.changelog.business.retention.repository;

import com.changelog.business.retention.model.DripCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DripCampaignRepository extends JpaRepository<DripCampaign, UUID> {

    List<DripCampaign> findByTenantId(UUID tenantId);

    List<DripCampaign> findByTenantIdAndTriggerEvent(UUID tenantId, String triggerEvent);

    List<DripCampaign> findByTenantIdAndStatus(UUID tenantId, String status);
}
