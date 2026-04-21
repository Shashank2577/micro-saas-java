package com.changelog.approval.controller;

import com.changelog.approval.model.ApprovalEvent;
import com.changelog.approval.model.WorkflowInstance;
import com.changelog.approval.service.WorkflowService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping("/documents/{docId}/workflows")
    public ResponseEntity<WorkflowInstance> initiateWorkflow(
            @PathVariable UUID docId,
            @RequestBody InitiateWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workflowService.initiateWorkflow(docId, request.getTemplateId()));
    }

    @GetMapping("/workflows/{workflowId}")
    public ResponseEntity<WorkflowInstance> getWorkflowStatus(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(workflowService.getWorkflowStatus(workflowId));
    }

    @PostMapping("/workflows/{workflowId}/cancel")
    public ResponseEntity<Void> cancelWorkflow(@PathVariable UUID workflowId) {
        workflowService.cancelWorkflow(workflowId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workflows/{workflowId}/audit")
    public ResponseEntity<List<ApprovalEvent>> getWorkflowAudit(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(workflowService.getWorkflowAudit(workflowId));
    }

    @Data
    public static class InitiateWorkflowRequest {
        private UUID templateId;
    }
}
