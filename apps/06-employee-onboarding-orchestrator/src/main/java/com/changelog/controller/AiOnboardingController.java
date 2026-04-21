package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.model.OnboardingTemplate;
import com.changelog.service.AiOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiOnboardingController {

    private final AiOnboardingService aiService;
    private final TenantResolver tenantResolver;

    @PostMapping("/ai/generate-plan")
    public ResponseEntity<OnboardingTemplate> generatePlan(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String jobTitle,
            @RequestParam String department) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        UUID userId = jwt != null && jwt.getSubject() != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(aiService.generatePlan(tenantId, jobTitle, department, userId));
    }

    @PostMapping("/templates/{templateId}/ai/write-descriptions")
    public ResponseEntity<OnboardingTemplate> writeDescriptions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId) {
        return ResponseEntity.ok(aiService.rewriteDescriptions(templateId, tenantResolver.getTenantId(jwt)));
    }
}
