package com.changelog.apikey.controller;

import com.changelog.apikey.dto.RegisterConsumerRequest;
import com.changelog.apikey.model.ApiConsumer;
import com.changelog.apikey.model.ApiKey;
import com.changelog.apikey.repository.ApiConsumerRepository;
import com.changelog.apikey.repository.ApiKeyRepository;
import com.changelog.apikey.service.ApiKeyService;
import com.changelog.config.TenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminController {
    private final ApiConsumerRepository consumerRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyService apiKeyService;
    private final TenantResolver tenantResolver;

    @GetMapping("/consumers")
    public List<ApiConsumer> listConsumers(@AuthenticationPrincipal Jwt jwt) {
        return consumerRepository.findByTenantId(tenantResolver.getTenantId(jwt));
    }

    @PostMapping("/consumers")
    public ApiConsumer registerConsumer(@AuthenticationPrincipal Jwt jwt, @RequestBody RegisterConsumerRequest request) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        ApiConsumer consumer = ApiConsumer.builder()
                .tenantId(tenantId)
                .externalId(request.getExternalId())
                .name(request.getName())
                .email(request.getEmail())
                .planTier(request.getPlanTier())
                .build();
        return consumerRepository.save(consumer);
    }

    @GetMapping("/keys")
    public List<ApiKey> listAllKeys(@AuthenticationPrincipal Jwt jwt) {
        return apiKeyRepository.findByTenantId(tenantResolver.getTenantId(jwt));
    }

    @PostMapping("/consumers/{consumerId}/keys/{keyId}/revoke")
    public void emergencyRevoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID consumerId, @PathVariable UUID keyId) {
        apiKeyService.revokeKey(tenantResolver.getTenantId(jwt), keyId, "admin:" + tenantResolver.getUserId(jwt));
    }
}
