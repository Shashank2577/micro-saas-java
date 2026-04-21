package com.changelog.controller;

import com.changelog.model.OnboardingInstance;
import com.changelog.service.OnboardingInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/onboardings")
@RequiredArgsConstructor
public class OnboardingInstanceController {

    private final OnboardingInstanceService instanceService;

    @GetMapping
    public ResponseEntity<List<OnboardingInstance>> listInstances(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return ResponseEntity.ok(instanceService.getActiveInstances(tenantId));
    }

    @PostMapping
    public ResponseEntity<OnboardingInstance> startOnboarding(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestParam UUID templateId,
            @RequestBody OnboardingInstance requestData) {
        return ResponseEntity.ok(instanceService.startOnboarding(tenantId, templateId, requestData, userId));
    }

    @GetMapping("/{onboardingId}")
    public ResponseEntity<OnboardingInstance> getInstance(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID onboardingId) {
        return ResponseEntity.ok(instanceService.getInstance(onboardingId, tenantId));
    }

    @PostMapping("/{onboardingId}/cancel")
    public ResponseEntity<Void> cancelOnboarding(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID onboardingId) {
        instanceService.cancelOnboarding(onboardingId, tenantId);
        return ResponseEntity.noContent().build();
    }
}
