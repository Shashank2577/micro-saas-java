package com.changelog.okr.repository;

import com.changelog.okr.model.Objective;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ObjectiveRepository extends JpaRepository<Objective, UUID> {
    List<Objective> findAllByTenantIdAndCycleId(UUID tenantId, UUID cycleId);
    List<Objective> findAllByParentId(UUID parentId);
}
