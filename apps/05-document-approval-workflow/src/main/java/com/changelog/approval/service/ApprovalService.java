package com.changelog.approval.service;

import com.changelog.approval.model.ApprovalEvent;
import com.changelog.approval.model.WorkflowInstance;
import com.changelog.approval.model.WorkflowStepInstance;
import com.changelog.approval.repository.ApprovalEventRepository;
import com.changelog.approval.repository.WorkflowInstanceRepository;
import com.changelog.approval.repository.WorkflowStepInstanceRepository;
import com.changelog.config.TenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final WorkflowStepInstanceRepository stepRepository;
    private final WorkflowInstanceRepository workflowRepository;
    private final ApprovalEventRepository eventRepository;
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
    public void approveStep(UUID stepId, String comment) {
        processAction(stepId, "approved", comment);
    }

    @Transactional
    public void rejectStep(UUID stepId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        processAction(stepId, "rejected", reason);
    }

    @Transactional
    public void requestChanges(UUID stepId, String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            throw new IllegalArgumentException("Comment is required to request changes");
        }
        processAction(stepId, "changes_requested", comment);
    }

    private void processAction(UUID stepId, String action, String comment) {
        WorkflowStepInstance step = stepRepository.findById(stepId)
                .orElseThrow(() -> new RuntimeException("Step not found"));
        WorkflowInstance workflow = step.getWorkflow();
        
        verifyTenant(workflow.getTenantId());
        
        // Ensure user is authorized
        UUID userId = getCurrentUserId();
        if (step.getAssigneeId() != null && !step.getAssigneeId().equals(userId)) {
            throw new RuntimeException("User not authorized to act on this step");
        }

        step.setStatus(action);
        step.setAction(action);
        step.setComment(comment);
        step.setCompletedAt(LocalDateTime.now());
        stepRepository.save(step);

        // Record event
        ApprovalEvent event = ApprovalEvent.builder()
                .tenantId(workflow.getTenantId())
                .documentId(workflow.getDocument().getId())
                .workflowId(workflow.getId())
                .stepId(step.getId())
                .actorId(userId)
                .eventType(action)
                .metadata(String.format("{\"comment\": \"%s\"}", comment != null ? comment.replace("\"", "\\\"") : ""))
                .build();
        eventRepository.save(event);

        updateWorkflowStatus(workflow, action);
    }

    private void updateWorkflowStatus(WorkflowInstance workflow, String action) {
        if ("rejected".equals(action)) {
            workflow.setStatus("rejected");
            workflow.setCompletedAt(LocalDateTime.now());
        } else if ("approved".equals(action)) {
            int currentStepNum = workflow.getCurrentStep();
            
            // Find next step
            WorkflowStepInstance nextStep = workflow.getSteps().stream()
                    .filter(s -> s.getStepNumber() == currentStepNum + 1)
                    .findFirst()
                    .orElse(null);

            if (nextStep != null) {
                workflow.setCurrentStep(currentStepNum + 1);
                nextStep.setStatus("in_progress");
                nextStep.setStartedAt(LocalDateTime.now());
                stepRepository.save(nextStep);
            } else {
                workflow.setStatus("approved");
                workflow.setCompletedAt(LocalDateTime.now());
                
                // Record completion
                ApprovalEvent event = ApprovalEvent.builder()
                        .tenantId(workflow.getTenantId())
                        .documentId(workflow.getDocument().getId())
                        .workflowId(workflow.getId())
                        .actorId(getCurrentUserId())
                        .eventType("workflow_completed")
                        .build();
                eventRepository.save(event);
            }
        } else if ("changes_requested".equals(action)) {
            workflow.setStatus("changes_requested");
        }
        
        workflowRepository.save(workflow);
    }

    @Transactional(readOnly = true)
    public List<WorkflowStepInstance> getPendingApprovals() {
        return stepRepository.findByAssigneeIdAndStatus(getCurrentUserId(), "in_progress");
    }

    @Transactional(readOnly = true)
    public List<ApprovalEvent> getApprovalHistory() {
        return eventRepository.findByActorIdOrderByOccurredAtDesc(getCurrentUserId());
    }
}
