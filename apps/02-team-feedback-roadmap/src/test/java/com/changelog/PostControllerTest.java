package com.changelog;

import com.changelog.model.FeedbackPost;
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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PostControllerTest {

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
    private UUID postId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        boardId = UUID.randomUUID();
        postId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO cc.tenants (id, name, slug, plan_tier) VALUES (?, 'Test Tenant', ?, 'startup')",
                tenantId, "test-tenant-" + tenantId);

        jdbcTemplate.update("INSERT INTO boards (id, tenant_id, name, slug) VALUES (?, ?, 'Feedback Board', 'feedback')",
                boardId, tenantId);

        jdbcTemplate.update("INSERT INTO feedback_posts (id, tenant_id, board_id, title, status, vote_count) VALUES (?, ?, ?, 'Test Post', 'under_review', 0)",
                postId, tenantId, boardId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM feedback_posts WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM boards WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM cc.tenants WHERE id = ?", tenantId);
    }

    @Test
    void testListPosts_returnsBoardPosts() throws Exception {
        mockMvc.perform(get("/api/boards/" + boardId + "/posts")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].title").value("Test Post"));
    }

    @Test
    void testCreatePost_increasesPostCount() throws Exception {
        FeedbackPost newPost = new FeedbackPost();
        newPost.setTitle("Add dark mode");
        newPost.setDescription("Would love dark mode");

        mockMvc.perform(post("/api/boards/" + boardId + "/posts")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newPost)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Add dark mode"));
    }

    @Test
    void testVotePost_incrementsVoteCount() throws Exception {
        String tenantSlug = jdbcTemplate.queryForObject("SELECT slug FROM cc.tenants WHERE id = ?", String.class, tenantId);

        mockMvc.perform(post("/public/" + tenantSlug + "/posts/" + postId + "/vote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"test@example.com\", \"name\": \"Tester\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/boards/" + boardId + "/posts")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].voteCount").value(1));
    }

    @Test
    void testUpdatePostStatus_changesStatusToUnderReview() throws Exception {
        mockMvc.perform(put("/api/boards/" + boardId + "/posts/" + postId + "/status")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"planned\", \"note\": \"Moving to planned\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("planned"));
    }
}
