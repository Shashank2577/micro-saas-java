package com.changelog;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PublicFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    private UUID tenantId;
    private String tenantSlug;
    private UUID boardId;
    private String boardSlug;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenantSlug = "test-tenant-" + tenantId;
        boardId = UUID.randomUUID();
        boardSlug = "main-board";

        jdbcTemplate.update("INSERT INTO cc.tenants (id, name, slug, plan_tier) VALUES (?, 'Test Tenant', ?, 'startup')",
                tenantId, tenantSlug);

        jdbcTemplate.update("INSERT INTO boards (id, tenant_id, name, slug, visibility) VALUES (?, ?, 'Main Board', ?, 'public')",
                boardId, tenantId, boardSlug);

        // One open post
        jdbcTemplate.update("INSERT INTO feedback_posts (id, tenant_id, board_id, title, status, is_public) VALUES (?, ?, ?, 'Open Post', 'under_review', true)",
                UUID.randomUUID(), tenantId, boardId);

        // One closed/private post
        jdbcTemplate.update("INSERT INTO feedback_posts (id, tenant_id, board_id, title, status, is_public) VALUES (?, ?, ?, 'Private Post', 'declined', false)",
                UUID.randomUUID(), tenantId, boardId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM feedback_posts WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM boards WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM cc.tenants WHERE id = ?", tenantId);
    }

    @Test
    void testPublicBoardPosts_returnsOpenPostsWithoutAuth() throws Exception {
        mockMvc.perform(get("/public/" + tenantSlug + "/boards/" + boardSlug + "/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].title").value("Open Post"));
    }

    @Test
    void testPublicSubmitPost_createsPostWithoutAuth() throws Exception {
        mockMvc.perform(post("/public/" + tenantSlug + "/boards/" + boardSlug + "/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Public suggestion\", \"submitterEmail\": \"user@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Public suggestion"));

        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM feedback_posts WHERE tenant_id = ? AND title = ?",
            Integer.class, tenantId, "Public suggestion");
        assertThat(count).isEqualTo(1);
    }
}
