package com.changelog;

import com.changelog.model.Board;
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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BoardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    private UUID tenantId;
    private UUID userId;
    private UUID boardId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        boardId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO cc.tenants (id, name, slug, plan_tier) VALUES (?, 'Test Tenant', ?, 'startup')",
                tenantId, "test-tenant-" + tenantId);

        jdbcTemplate.update("INSERT INTO boards (id, tenant_id, name, slug, visibility) VALUES (?, ?, 'Main Board', 'main-board', 'public')",
                boardId, tenantId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM feedback_posts WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM boards WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM cc.tenants WHERE id = ?", tenantId);
    }

    @Test
    void testListBoards_returnsCurrentTenantBoards() throws Exception {
        mockMvc.perform(get("/api/boards")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Main Board"));
    }

    @Test
    void testListBoards_excludesOtherTenantBoards() throws Exception {
        UUID otherTenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO cc.tenants (id, name, slug, plan_tier) VALUES (?, 'Other Tenant', ?, 'startup')",
                otherTenantId, "other-tenant-" + otherTenantId);
        jdbcTemplate.update("INSERT INTO boards (id, tenant_id, name, slug, visibility) VALUES (?, ?, 'Other Board', 'other-board', 'public')",
                UUID.randomUUID(), otherTenantId);

        try {
            mockMvc.perform(get("/api/boards")
                            .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("Main Board"));
        } finally {
            jdbcTemplate.update("DELETE FROM boards WHERE tenant_id = ?", otherTenantId);
            jdbcTemplate.update("DELETE FROM cc.tenants WHERE id = ?", otherTenantId);
        }
    }

    @Test
    void testCreateBoard_persistsBoard() throws Exception {
        Board newBoard = new Board();
        newBoard.setName("Feature Requests");
        newBoard.setSlug("feature-requests");
        newBoard.setVisibility("public");

        mockMvc.perform(post("/api/boards")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newBoard)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Feature Requests"))
            .andExpect(jsonPath("$.tenantId").value(tenantId.toString()));
    }

    @Test
    void testGetBoard_returns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/boards/" + UUID.randomUUID())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void testCreateBoard_returns400WhenNameMissing() throws Exception {
        Board invalidBoard = new Board();
        invalidBoard.setSlug("no-name");

        mockMvc.perform(post("/api/boards")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidBoard)))
            .andExpect(status().isBadRequest());
    }
}
