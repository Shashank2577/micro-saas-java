package com.changelog.approval.repository;

import com.changelog.approval.model.WorkflowStepInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowStepInstanceRepository extends JpaRepository<WorkflowStepInstance, UUID> {
    List<WorkflowStepInstance> findByAssigneeIdAndStatus(UUID assigneeId, String status);
}
