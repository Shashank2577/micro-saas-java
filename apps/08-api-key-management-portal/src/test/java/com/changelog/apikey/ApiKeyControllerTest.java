package com.changelog.apikey;

import com.changelog.apikey.dto.CreateKeyRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    private UUID tenantId;
    private UUID consumerId;
    private UUID keyId;
    private String dummyHash = "$2a$10$AAAAAAAAAAAAAAAAAAAAAA"; // placeholder bcrypt hash

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        consumerId = UUID.randomUUID();
        keyId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO cc.tenants (id, name, slug, plan_tier) VALUES (?, 'Test Tenant', ?, 'startup')",
                tenantId, "test-tenant-" + tenantId);

        jdbcTemplate.update("INSERT INTO api_consumers (id, tenant_id, external_id, name) VALUES (?, ?, 'ext-1', 'Test Consumer')",
                consumerId, tenantId);

        jdbcTemplate.update("INSERT INTO api_keys (id, tenant_id, consumer_id, name, key_prefix, key_hash, status, environment) VALUES (?, ?, ?, 'Test Key', 'sk_test_', ?, 'active', 'production')",
                keyId, tenantId, consumerId, dummyHash);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM key_usage_events WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM api_keys WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM api_consumers WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM cc.tenants WHERE id = ?", tenantId);
    }

    @Test
    void testListApiKeys_returnsActiveKeys() throws Exception {
        mockMvc.perform(get("/api/keys")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Test Key"));
    }

    @Test
    void testCreateApiKey_returnsPlaintextKeyOnce() throws Exception {
        CreateKeyRequest request = new CreateKeyRequest();
        request.setConsumerId(consumerId);
        request.setName("New Key");
        request.setEnvironment("production");
        request.setScopes(List.of("read"));

        MvcResult result = mockMvc.perform(post("/portal/keys")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        String plaintextKey = result.getResponse().getContentAsString();
        assertThat(plaintextKey).startsWith("sk_live_");

        String storedHash = jdbcTemplate.queryForObject(
            "SELECT key_hash FROM api_keys WHERE tenant_id = ? AND name = ?",
            String.class, tenantId, "New Key");

        assertThat(storedHash).isNotNull();
        assertThat(storedHash).isNotEqualTo(plaintextKey);
        assertThat(storedHash).startsWith("$2a$");
    }

    @Test
    void testRevokeApiKey_changesStatusToRevoked() throws Exception {
        mockMvc.perform(post("/portal/keys/" + keyId + "/revoke")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()))))
            .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM api_keys WHERE id = ?",
            String.class, keyId);
        assertThat(status).isEqualTo("revoked");
    }

    @Test
    void testRevokeKey_returns404ForOtherTenantKey() throws Exception {
        UUID otherTenantId = UUID.randomUUID();
        mockMvc.perform(post("/portal/keys/" + keyId + "/revoke")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", otherTenantId.toString()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void testValidateApiKey_returnsValidForActiveKey() throws Exception {
        // We can't easily test with dummyHash because we don't know the plaintext.
        // Let's create a key properly via the service/controller if possible, or just mock the hash.
        // Actually, we can just use the create endpoint to get a real key.

        CreateKeyRequest request = new CreateKeyRequest();
        request.setConsumerId(consumerId);
        request.setName("Validation Key");
        request.setEnvironment("production");
        request.setScopes(List.of("read"));

        MvcResult result = mockMvc.perform(post("/portal/keys")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        String plaintextKey = result.getResponse().getContentAsString();

        mockMvc.perform(post("/validate")
                .header("X-API-KEY", plaintextKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenant_id").value(tenantId.toString()));
    }

    @Test
    void testRotateKey_createsNewKeyAndRevokesOld() throws Exception {
        MvcResult result = mockMvc.perform(post("/portal/keys/" + keyId + "/rotate")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()))))
            .andExpect(status().isOk())
            .andReturn();

        String newPlaintextKey = result.getResponse().getContentAsString();
        assertThat(newPlaintextKey).startsWith("sk_live_");

        String oldStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM api_keys WHERE id = ?",
            String.class, keyId);
        assertThat(oldStatus).isEqualTo("revoked");

        String rotationOf = jdbcTemplate.queryForObject(
            "SELECT rotation_of FROM api_keys WHERE key_prefix = ?",
            String.class, newPlaintextKey.substring(0, 8));
        assertThat(rotationOf).isEqualTo(keyId.toString());
    }

    @Test
    void testCreateApiKey_returns400WhenConsumerIdMissing() throws Exception {
        CreateKeyRequest request = new CreateKeyRequest();
        request.setName("No Consumer Key");

        mockMvc.perform(post("/portal/keys")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
