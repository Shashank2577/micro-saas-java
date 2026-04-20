package com.changelog.approval.controller;

import com.changelog.approval.model.ApprovalEvent;
import com.changelog.approval.model.WorkflowStepInstance;
import com.changelog.approval.service.ApprovalService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/steps/{stepId}/approve")
    public ResponseEntity<Void> approveStep(
            @PathVariable UUID stepId,
            @RequestBody(required = false) ActionRequest request) {
        String comment = request != null ? request.getComment() : null;
        approvalService.approveStep(stepId, comment);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/steps/{stepId}/request-changes")
    public ResponseEntity<Void> requestChanges(
            @PathVariable UUID stepId,
            @RequestBody ActionRequest request) {
        approvalService.requestChanges(stepId, request.getComment());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/steps/{stepId}/reject")
    public ResponseEntity<Void> rejectStep(
            @PathVariable UUID stepId,
            @RequestBody ActionRequest request) {
        approvalService.rejectStep(stepId, request.getComment()); // using comment as reason
        return ResponseEntity.ok().build();
    }

    @GetMapping("/approvals/pending")
    public ResponseEntity<List<WorkflowStepInstance>> getPendingApprovals() {
        return ResponseEntity.ok(approvalService.getPendingApprovals());
    }

    @GetMapping("/approvals/history")
    public ResponseEntity<List<ApprovalEvent>> getApprovalHistory() {
        return ResponseEntity.ok(approvalService.getApprovalHistory());
    }

    @Data
    public static class ActionRequest {
        private String comment;
    }
}
