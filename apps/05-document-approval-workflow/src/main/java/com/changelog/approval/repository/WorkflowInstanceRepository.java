package com.changelog.approval.repository;

import com.changelog.approval.model.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
    List<WorkflowInstance> findByTenantId(UUID tenantId);
}
