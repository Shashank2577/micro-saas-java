package com.changelog.apikey.controller;

import com.changelog.apikey.dto.CreateKeyRequest;
import com.changelog.apikey.model.ApiKey;
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
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalController {
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyService apiKeyService;
    private final TenantResolver tenantResolver;

    @GetMapping("/keys")
    public List<ApiKey> listMyKeys(@AuthenticationPrincipal Jwt jwt) {
        // In consumer portal, the JWT subject might be the consumer's user ID
        // For simplicity, we assume tenant_id is still present
        UUID tenantId = tenantResolver.getTenantId(jwt);
        // We'd also need consumer_id from the token, here assuming it's available or resolved
        return apiKeyRepository.findByTenantId(tenantId); 
    }

    @PostMapping("/keys")
    public String createKey(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateKeyRequest request) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return apiKeyService.createKey(tenantId, request.getConsumerId(), request.getName(), 
                request.getScopes(), request.getEnvironment(), "consumer:" + tenantResolver.getUserId(jwt));
    }

    @PostMapping("/keys/{keyId}/revoke")
    public void revokeKey(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID keyId) {
        apiKeyService.revokeKey(tenantResolver.getTenantId(jwt), keyId, "consumer:" + tenantResolver.getUserId(jwt));
    }

    @PostMapping("/keys/{keyId}/rotate")
    public String rotateKey(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID keyId) {
        return apiKeyService.rotateKey(tenantResolver.getTenantId(jwt), keyId, "consumer:" + tenantResolver.getUserId(jwt));
    }
}
