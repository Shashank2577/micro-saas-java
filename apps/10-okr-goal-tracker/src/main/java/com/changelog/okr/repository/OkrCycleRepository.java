package com.changelog.okr.repository;

import com.changelog.okr.model.OkrCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OkrCycleRepository extends JpaRepository<OkrCycle, UUID> {
    List<OkrCycle> findAllByTenantId(UUID tenantId);
}
