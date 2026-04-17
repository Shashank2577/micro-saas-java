package com.changelog.config;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface TenantResolver {
    UUID getTenantId(Jwt jwt);
    UUID getUserId(Jwt jwt);
}
