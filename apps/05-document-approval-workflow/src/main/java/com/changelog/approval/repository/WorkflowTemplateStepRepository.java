package com.changelog.approval.repository;

import com.changelog.approval.model.WorkflowTemplateStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WorkflowTemplateStepRepository extends JpaRepository<WorkflowTemplateStep, UUID> {
}
