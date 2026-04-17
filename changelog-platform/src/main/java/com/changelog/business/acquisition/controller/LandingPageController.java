package com.changelog.business.acquisition.controller;

import com.changelog.business.acquisition.dto.CreateLandingPageRequest;
import com.changelog.business.acquisition.dto.CreateLandingPageRequest.LandingVariantRequest;
import com.changelog.business.acquisition.model.LandingPage;
import com.changelog.business.acquisition.model.LandingVariant;
import com.changelog.business.acquisition.service.LandingPageService;
import com.changelog.business.orchestration.event.BusinessEvent;
import com.changelog.business.orchestration.service.BusinessEventPublisher;
import com.changelog.config.TenantResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/landing-pages")
@RequiredArgsConstructor
public class LandingPageController {

    private final LandingPageService landingPageService;
    private final BusinessEventPublisher eventPublisher;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<LandingPage>> getLandingPages(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(landingPageService.getAllPages(tenantId));
    }

    @PostMapping
    public ResponseEntity<LandingPage> createLandingPage(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateLandingPageRequest request) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        LandingPage page = landingPageService.createPage(tenantId, request);

        eventPublisher.publish(
                BusinessEvent.BusinessEventType.LANDING_PAGE_VIEWED,
                tenantId,
                null,
                Map.of("pageId", page.getId(), "slug", page.getSlug())
        );

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{pageId}")
    public ResponseEntity<LandingPage> getLandingPage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pageId) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(landingPageService.getPage(tenantId, pageId));
    }

    @PostMapping("/{pageId}/activate")
    public ResponseEntity<LandingPage> activateLandingPage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pageId) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(landingPageService.activatePage(tenantId, pageId));
    }

    @PostMapping("/{pageId}/variants")
    public ResponseEntity<LandingVariant> createVariant(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pageId,
            @Valid @RequestBody LandingVariantRequest request) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(landingPageService.createVariant(tenantId, pageId, request));
    }

    @PostMapping("/public/{variantId}/view")
    public ResponseEntity<Void> trackView(@PathVariable UUID variantId) {
        landingPageService.recordView(variantId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/public/{variantId}/convert")
    public ResponseEntity<Void> trackConversion(@PathVariable UUID variantId) {
        landingPageService.recordConversion(variantId);
        return ResponseEntity.ok().build();
    }
}
