package com.changelog.config;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("!local")
public class JwtTenantResolver implements TenantResolver {

    @Override
    public UUID getTenantId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("tenant_id"));
    }

    @Override
    public UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
