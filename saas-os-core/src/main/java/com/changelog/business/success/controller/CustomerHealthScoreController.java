package com.changelog.business.success.controller;

import com.changelog.business.success.model.CustomerHealthScore;
import com.changelog.business.success.repository.CustomerHealthScoreRepository;
import com.changelog.business.success.service.CustomerHealthScoringService;
import com.changelog.config.TenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/health-scores")
@RequiredArgsConstructor
public class CustomerHealthScoreController {

    private final CustomerHealthScoringService customerHealthScoringService;
    private final CustomerHealthScoreRepository customerHealthScoreRepository;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<CustomerHealthScore>> getAllHealthScores(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(customerHealthScoreRepository.findAll()
                .stream()
                .filter(s -> s.getTenantId().equals(tenantId))
                .toList());
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerHealthScore> getLatestHealthScore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID customerId) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        return customerHealthScoreRepository.findLatestByTenantAndCustomer(tenantId, customerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{customerId}/recalculate")
    public ResponseEntity<CustomerHealthScore> recalculateHealthScore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID customerId) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        customerHealthScoringService.calculateHealthScore(tenantId, customerId);
        return customerHealthScoreRepository.findLatestByTenantAndCustomer(tenantId, customerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/at-risk")
    public ResponseEntity<List<CustomerHealthScore>> getAtRiskCustomers(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(customerHealthScoreRepository.findHighRiskCustomers(tenantId));
    }
}
