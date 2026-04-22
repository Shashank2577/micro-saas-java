package com.changelog.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTenantResolverTest {

    private final JwtTenantResolver tenantResolver = new JwtTenantResolver();

    @Test
    void testGetTenantId_extractsFromJwtClaim() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("tenant_id", tenantId.toString())
                .subject(userId.toString())
                .build();

        UUID result = tenantResolver.getTenantId(jwt);

        assertThat(result).isEqualTo(tenantId);
    }

    @Test
    void testGetTenantId_throwsWhenClaimMissing() {
        UUID userId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .build();

        assertThatThrownBy(() -> tenantResolver.getTenantId(jwt))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetTenantId_throwsWhenClaimIsInvalidUuid() {
        UUID userId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("tenant_id", "not-a-uuid")
                .subject(userId.toString())
                .build();

        assertThatThrownBy(() -> tenantResolver.getTenantId(jwt))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
