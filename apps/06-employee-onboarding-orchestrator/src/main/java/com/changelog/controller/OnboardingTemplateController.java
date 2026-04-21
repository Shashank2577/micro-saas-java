package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.model.OnboardingTemplate;
import com.changelog.model.TemplateTask;
import com.changelog.service.OnboardingTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class OnboardingTemplateController {

    private final OnboardingTemplateService templateService;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<OnboardingTemplate>> listTemplates(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(templateService.getActiveTemplates(tenantResolver.getTenantId(jwt)));
    }

    @PostMapping
    public ResponseEntity<OnboardingTemplate> createTemplate(@AuthenticationPrincipal Jwt jwt, @RequestBody OnboardingTemplate template) {
        template.setTenantId(tenantResolver.getTenantId(jwt));
        return ResponseEntity.ok(templateService.createTemplate(template));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<OnboardingTemplate> getTemplate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID templateId) {
        return ResponseEntity.ok(templateService.getTemplate(templateId, tenantResolver.getTenantId(jwt)));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<OnboardingTemplate> updateTemplate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId,
            @RequestBody OnboardingTemplate template) {
        return ResponseEntity.ok(templateService.updateTemplate(templateId, tenantResolver.getTenantId(jwt), template));
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> archiveTemplate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID templateId) {
        templateService.archiveTemplate(templateId, tenantResolver.getTenantId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{templateId}/tasks")
    public ResponseEntity<OnboardingTemplate> replaceTasks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID templateId,
            @RequestBody List<TemplateTask> tasks) {
        return ResponseEntity.ok(templateService.replaceTasks(templateId, tenantResolver.getTenantId(jwt), tasks));
    }
}
