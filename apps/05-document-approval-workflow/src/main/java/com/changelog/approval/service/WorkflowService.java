package com.changelog.approval.service;

import com.changelog.approval.model.*;
import com.changelog.approval.repository.ApprovalEventRepository;
import com.changelog.approval.repository.WorkflowInstanceRepository;
import com.changelog.config.TenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowInstanceRepository workflowRepository;
    private final ApprovalEventRepository eventRepository;
    private final DocumentService documentService;
    private final WorkflowTemplateService templateService;
    private final TenantResolver tenantResolver;

    private Jwt getCurrentJwt() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt) {
            return (Jwt) principal;
        }
        return null;
    }

    private UUID getCurrentTenantId() {
        return tenantResolver.getTenantId(getCurrentJwt());
    }

    private UUID getCurrentUserId() {
        return tenantResolver.getUserId(getCurrentJwt());
    }

    private void verifyTenant(UUID tenantId) {
        if (!getCurrentTenantId().equals(tenantId)) {
            throw new RuntimeException("Access Denied");
        }
    }

    @Transactional
    public WorkflowInstance initiateWorkflow(UUID documentId, UUID templateId) {
        Document document = documentService.getDocument(documentId);
        WorkflowTemplate template = templateService.getTemplate(templateId);
        UUID tenantId = getCurrentTenantId();
        UUID userId = getCurrentUserId();

        WorkflowInstance workflow = WorkflowInstance.builder()
                .document(document)
                .template(template)
                .tenantId(tenantId)
                .initiatedBy(userId)
                .status("in_progress")
                .currentStep(1)
                .build();

        List<WorkflowStepInstance> stepInstances = new ArrayList<>();
        for (WorkflowTemplateStep tplStep : template.getSteps()) {
            WorkflowStepInstance stepInstance = WorkflowStepInstance.builder()
                    .workflow(workflow)
                    .stepNumber(tplStep.getStepNumber())
                    .stepName(tplStep.getName())
                    .status(tplStep.getStepNumber() == 1 ? "in_progress" : "pending")
                    // Basic assignment logic - taking first if multiple, or handling role based assignment in a real scenario
                    .assigneeId(tplStep.getApproverIds() != null && !tplStep.getApproverIds().isEmpty() ? tplStep.getApproverIds().get(0) : null)
                    .startedAt(tplStep.getStepNumber() == 1 ? LocalDateTime.now() : null)
                    .build();
            stepInstances.add(stepInstance);
        }
        workflow.setSteps(stepInstances);

        workflow = workflowRepository.save(workflow);

        // Record Audit Event
        ApprovalEvent event = ApprovalEvent.builder()
                .tenantId(tenantId)
                .documentId(documentId)
                .workflowId(workflow.getId())
                .actorId(userId)
                .eventType("workflow_started")
                .build();
        eventRepository.save(event);

        return workflow;
    }

    @Transactional(readOnly = true)
    public WorkflowInstance getWorkflowStatus(UUID workflowId) {
        WorkflowInstance workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));
        verifyTenant(workflow.getTenantId());
        return workflow;
    }

    @Transactional
    public void cancelWorkflow(UUID workflowId) {
        WorkflowInstance workflow = getWorkflowStatus(workflowId);
        workflow.setStatus("cancelled");
        workflow.setCompletedAt(LocalDateTime.now());
        workflowRepository.save(workflow);

        ApprovalEvent event = ApprovalEvent.builder()
                .tenantId(workflow.getTenantId())
                .documentId(workflow.getDocument().getId())
                .workflowId(workflow.getId())
                .actorId(getCurrentUserId())
                .eventType("workflow_cancelled")
                .build();
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<ApprovalEvent> getWorkflowAudit(UUID workflowId) {
        WorkflowInstance workflow = getWorkflowStatus(workflowId); // Ensures tenant verification
        return eventRepository.findByWorkflowIdOrderByOccurredAtAsc(workflowId);
    }
}
