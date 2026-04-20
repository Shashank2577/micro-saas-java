package com.changelog.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("tenantSecurity")
@RequiredArgsConstructor
public class TenantSecurityService {

    private final TenantResolver tenantResolver;

    /**
     * Checks if the authenticated user belongs to the specified tenant.
     */
    public boolean isMember(Authentication authentication, UUID tenantId) {
        if (authentication == null || tenantId == null) return false;
        
        // Handle local profile (permit all) or extract from JWT
        UUID userTenantId = getTenantId(authentication);
        return tenantId.equals(userTenantId);
    }

    private UUID getTenantId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            return tenantResolver.getTenantId(jwtToken.getToken());
        }
        
        // Fallback for local profile or other authentication types
        // In local profile, LocalTenantResolver.DEV_TENANT_ID is used
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return tenantResolver.getTenantId(jwt);
        }
        
        return null;
    }
}
