package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.model.OnboardingInstance;
import com.changelog.service.OnboardingInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/onboardings")
@RequiredArgsConstructor
public class OnboardingInstanceController {

    private final OnboardingInstanceService instanceService;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<OnboardingInstance>> listInstances(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(instanceService.getActiveInstances(tenantResolver.getTenantId(jwt)));
    }

    @PostMapping
    public ResponseEntity<OnboardingInstance> startOnboarding(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID templateId,
            @RequestBody OnboardingInstance requestData) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        UUID userId = jwt != null && jwt.getSubject() != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(instanceService.startOnboarding(tenantId, templateId, requestData, userId));
    }

    @GetMapping("/{onboardingId}")
    public ResponseEntity<OnboardingInstance> getInstance(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID onboardingId) {
        return ResponseEntity.ok(instanceService.getInstance(onboardingId, tenantResolver.getTenantId(jwt)));
    }

    @PostMapping("/{onboardingId}/cancel")
    public ResponseEntity<Void> cancelOnboarding(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID onboardingId) {
        instanceService.cancelOnboarding(onboardingId, tenantResolver.getTenantId(jwt));
        return ResponseEntity.noContent().build();
    }
}
