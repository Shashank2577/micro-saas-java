package com.changelog.business.intelligence.controller;

import com.changelog.business.intelligence.model.FunnelAnalytics;
import com.changelog.business.intelligence.model.UnitEconomics;
import com.changelog.business.intelligence.service.AnalyticsService;
import com.changelog.business.intelligence.service.FunnelAnalyticsService;
import com.changelog.business.intelligence.service.UnitEconomicsService;
import com.changelog.config.TenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UnitEconomicsService unitEconomicsService;
    private final FunnelAnalyticsService funnelAnalyticsService;
    private final TenantResolver tenantResolver;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(analyticsService.getDashboardMetrics(tenantId));
    }

    @GetMapping("/unit-economics")
    public ResponseEntity<List<UnitEconomics>> getUnitEconomics(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "12") int months) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(unitEconomicsService.getHistory(tenantId, months));
    }

    @GetMapping("/unit-economics/latest")
    public ResponseEntity<UnitEconomics> getLatestUnitEconomics(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return unitEconomicsService.getLatest(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/unit-economics/health-status")
    public ResponseEntity<Map<String, String>> getLtvCacHealthStatus(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        String status = unitEconomicsService.getLtvCacHealthStatus(tenantId);
        return ResponseEntity.ok(Map.of(
                "status", status,
                "description", getHealthStatusDescription(status)
        ));
    }

    @PostMapping("/funnels/{funnelName}/calculate")
    public ResponseEntity<FunnelAnalytics> calculateFunnel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String funnelName,
            @RequestParam(defaultValue = "free_to_paid") String funnelType,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().minusDays(30)}") LocalDate startDate,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now()}") LocalDate endDate) {

        UUID tenantId = tenantResolver.getTenantId(jwt);

        List<FunnelAnalyticsService.FunnelStep> steps;
        switch (funnelType) {
            case "free_to_paid":
                steps = FunnelAnalyticsService.StandardFunnels.freeToPaid();
                break;
            case "onboarding":
                steps = FunnelAnalyticsService.StandardFunnels.onboarding();
                break;
            case "activation":
                steps = FunnelAnalyticsService.StandardFunnels.activation();
                break;
            default:
                throw new IllegalArgumentException("Unknown funnel type: " + funnelType);
        }

        funnelAnalyticsService.calculateFunnel(tenantId, funnelName, steps, startDate, endDate);

        return funnelAnalyticsService.getLatest(tenantId, funnelName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/funnels")
    public ResponseEntity<List<FunnelAnalytics>> getFunnels(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(funnelAnalyticsService.getAllFunnels(tenantId));
    }

    @GetMapping("/funnels/{funnelName}")
    public ResponseEntity<List<FunnelAnalytics>> getFunnel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String funnelName) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(funnelAnalyticsService.getFunnel(tenantId, funnelName));
    }

    private String getHealthStatusDescription(String status) {
        return switch (status) {
            case "excellent" -> "LTV:CAC ratio > 5:1 - Excellent performance";
            case "healthy" -> "LTV:CAC ratio 3:1 to 5:1 - On track";
            case "warning" -> "LTV:CAC ratio 1:1 to 3:1 - Needs improvement";
            case "critical" -> "LTV:CAC ratio < 1:1 - Losing money on each customer";
            default -> "Unable to calculate - Not enough data";
        };
    }
}
