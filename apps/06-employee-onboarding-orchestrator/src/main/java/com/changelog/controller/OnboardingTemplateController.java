package com.changelog.controller;

import com.changelog.model.OnboardingTemplate;
import com.changelog.model.TemplateTask;
import com.changelog.service.OnboardingTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class OnboardingTemplateController {

    private final OnboardingTemplateService templateService;

    @GetMapping
    public ResponseEntity<List<OnboardingTemplate>> listTemplates(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return ResponseEntity.ok(templateService.getActiveTemplates(tenantId));
    }

    @PostMapping
    public ResponseEntity<OnboardingTemplate> createTemplate(@RequestHeader("X-Tenant-ID") UUID tenantId, @RequestBody OnboardingTemplate template) {
        template.setTenantId(tenantId);
        return ResponseEntity.ok(templateService.createTemplate(template));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<OnboardingTemplate> getTemplate(@RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID templateId) {
        return ResponseEntity.ok(templateService.getTemplate(templateId, tenantId));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<OnboardingTemplate> updateTemplate(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID templateId,
            @RequestBody OnboardingTemplate template) {
        return ResponseEntity.ok(templateService.updateTemplate(templateId, tenantId, template));
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> archiveTemplate(@RequestHeader("X-Tenant-ID") UUID tenantId, @PathVariable UUID templateId) {
        templateService.archiveTemplate(templateId, tenantId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{templateId}/tasks")
    public ResponseEntity<OnboardingTemplate> replaceTasks(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID templateId,
            @RequestBody List<TemplateTask> tasks) {
        return ResponseEntity.ok(templateService.replaceTasks(templateId, tenantId, tasks));
    }
}
