package com.changelog.security;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    public String generateToken(UUID tenantId, UUID userId, String email) {
        // Dummy implementation for tests. The actual app uses Keycloak via OAuth2 resource server.
        return "dummyHeader." + tenantId.toString() + "_" + userId.toString() + ".dummySignature";
    }

    public boolean validateToken(String token) {
        return token != null && token.contains(".");
    }

    public String getTenantId(String token) {
        String[] parts = token.split("\\.");
        if (parts.length > 1) {
            String payload = parts[1];
            return payload.split("_")[0];
        }
        return UUID.randomUUID().toString();
    }
}