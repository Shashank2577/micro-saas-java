package com.changelog.controller;

import com.changelog.model.AiQaSession;
import com.changelog.service.AiKnowledgeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiKnowledgeService aiService;

    @PostMapping("/ask")
    public ResponseEntity<AiQaSession> askQuestion(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-User-ID", required = false) UUID userId,
            @RequestBody AskRequest request) {
        return ResponseEntity.ok(aiService.askQuestion(tenantId, userId, request.getQuestion()));
    }

    @PostMapping("/ask/{sessionId}/feedback")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable UUID sessionId,
            @RequestBody FeedbackRequest request) {
        aiService.submitFeedback(sessionId, request.getFeedback());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/gaps")
    public ResponseEntity<List<String>> getGapReport(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return ResponseEntity.ok(aiService.getGapReport(tenantId));
    }

    @Data
    public static class AskRequest {
        private String question;
    }

    @Data
    public static class FeedbackRequest {
        private String feedback;
    }
}