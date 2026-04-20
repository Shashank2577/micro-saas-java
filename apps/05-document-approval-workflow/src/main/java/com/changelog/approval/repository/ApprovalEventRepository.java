package com.changelog.approval.repository;

import com.changelog.approval.model.ApprovalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalEventRepository extends JpaRepository<ApprovalEvent, UUID> {
    List<ApprovalEvent> findByWorkflowIdOrderByOccurredAtAsc(UUID workflowId);
    List<ApprovalEvent> findByActorIdOrderByOccurredAtDesc(UUID actorId);
}
