package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.model.AiQaSession;
import com.changelog.service.AiKnowledgeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiKnowledgeService aiService;
    private final TenantResolver tenantResolver;

    @PostMapping("/ask")
    public ResponseEntity<AiQaSession> askQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AskRequest request) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        UUID userId = jwt != null && jwt.getSubject() != null ? UUID.fromString(jwt.getSubject()) : null;
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
    public ResponseEntity<List<String>> getGapReport(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(aiService.getGapReport(tenantResolver.getTenantId(jwt)));
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
