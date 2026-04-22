package com.changelog.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LocalTenantResolverTest {

    private final LocalTenantResolver tenantResolver = new LocalTenantResolver();

    @Test
    void testGetTenantId_returnsFixedUuid() {
        Jwt anyJwt = mock(Jwt.class);

        UUID tenantId1 = tenantResolver.getTenantId(anyJwt);
        UUID tenantId2 = tenantResolver.getTenantId(anyJwt);

        assertThat(tenantId1).isNotNull();
        assertThat(tenantId1).isEqualTo(LocalTenantResolver.DEV_TENANT_ID);
        assertThat(tenantId1).isEqualTo(tenantId2);
    }
}
