package com.changelog.controller;

import com.changelog.model.OnboardingTemplate;
import com.changelog.service.AiOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiOnboardingController {

    private final AiOnboardingService aiService;

    @PostMapping("/ai/generate-plan")
    public ResponseEntity<OnboardingTemplate> generatePlan(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestParam String jobTitle,
            @RequestParam String department) {
        return ResponseEntity.ok(aiService.generatePlan(tenantId, jobTitle, department, userId));
    }

    @PostMapping("/templates/{templateId}/ai/write-descriptions")
    public ResponseEntity<OnboardingTemplate> writeDescriptions(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID templateId) {
        return ResponseEntity.ok(aiService.rewriteDescriptions(templateId, tenantId));
    }
}
