package com.changelog.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantSecurityServiceTest {

    @Mock
    private TenantResolver tenantResolver;

    private TenantSecurityService securityService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        securityService = new TenantSecurityService(tenantResolver);
    }

    @Test
    void shouldReturnTrueWhenTenantIdsMatchViaToken() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tenant_id", tenantId.toString())
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        
        when(tenantResolver.getTenantId(jwt)).thenReturn(tenantId);
        
        assertTrue(securityService.isMember(auth, tenantId));
    }

    @Test
    void shouldReturnTrueWhenTenantIdsMatchViaPrincipal() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tenant_id", tenantId.toString())
                .build();
        org.springframework.security.authentication.TestingAuthenticationToken auth = 
                new org.springframework.security.authentication.TestingAuthenticationToken(jwt, null);
        
        when(tenantResolver.getTenantId(jwt)).thenReturn(tenantId);
        
        assertTrue(securityService.isMember(auth, tenantId));
    }

    @Test
    void shouldReturnFalseWhenTenantIdsDoNotMatch() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tenant_id", UUID.randomUUID().toString())
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        
        when(tenantResolver.getTenantId(jwt)).thenReturn(UUID.randomUUID());
        
        assertFalse(securityService.isMember(auth, tenantId));
    }

    @Test
    void shouldReturnFalseWhenAuthenticationIsNull() {
        assertFalse(securityService.isMember(null, tenantId));
    }
}
