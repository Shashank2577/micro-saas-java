package com.changelog.apikey;

import com.changelog.apikey.model.ApiConsumer;
import com.changelog.apikey.model.ApiKey;
import com.changelog.apikey.repository.ApiConsumerRepository;
import com.changelog.apikey.repository.ApiKeyRepository;
import com.changelog.apikey.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
public class ApiKeyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiConsumerRepository consumerRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    private UUID tenantId;
    private UUID consumerId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        ApiConsumer consumer = ApiConsumer.builder()
                .tenantId(tenantId)
                .externalId("ext-123")
                .name("Test Consumer")
                .build();
        consumer = consumerRepository.save(consumer);
        consumerId = consumer.getId();
    }

    @Test
    void testCreateAndValidateKey() throws Exception {
        String rawKey = apiKeyService.createKey(tenantId, consumerId, "Test Key", List.of("read"), "production", "user-1");

        mockMvc.perform(post("/validate")
                .header("X-API-KEY", rawKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consumer_id").value(consumerId.toString()))
            .andExpect(jsonPath("$.tenant_id").value(tenantId.toString()));
    }

    @Test
    void testRevokeKey() throws Exception {
        String rawKey = apiKeyService.createKey(tenantId, consumerId, "Test Key", List.of("read"), "production", "user-1");
        ApiKey key = apiKeyRepository.findByKeyPrefix("sk_live_" + rawKey.substring(8, 16)).get(0);

        mockMvc.perform(post("/portal/keys/" + key.getId() + "/revoke")
                .with(jwt().claim("tenant_id", tenantId.toString()).claim("sub", UUID.randomUUID().toString())))
            .andExpect(status().isOk());

        mockMvc.perform(post("/validate")
                .header("X-API-KEY", rawKey))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testTenantIsolation() throws Exception {
        UUID otherTenantId = UUID.randomUUID();
        String rawKey = apiKeyService.createKey(tenantId, consumerId, "Test Key", List.of("read"), "production", "user-1");

        mockMvc.perform(get("/api/keys")
                .with(jwt().claim("tenant_id", otherTenantId.toString()).claim("sub", UUID.randomUUID().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }
}
