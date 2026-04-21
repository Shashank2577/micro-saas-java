package com.changelog.okr.repository;

import com.changelog.okr.model.KeyResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KeyResultRepository extends JpaRepository<KeyResult, UUID> {
    List<KeyResult> findAllByObjectiveId(UUID objectiveId);
    List<KeyResult> findAllByOwnerIdAndTenantId(UUID ownerId, UUID tenantId);
}
