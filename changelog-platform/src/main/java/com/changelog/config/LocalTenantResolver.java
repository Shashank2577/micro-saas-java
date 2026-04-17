package com.changelog.config;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("local")
public class LocalTenantResolver implements TenantResolver {

    public static final UUID DEV_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    public static final UUID DEV_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @Override
    public UUID getTenantId(Jwt jwt) {
        return DEV_TENANT_ID;
    }

    @Override
    public UUID getUserId(Jwt jwt) {
        return DEV_USER_ID;
    }
}
