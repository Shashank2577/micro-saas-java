package com.changelog.api;

import com.changelog.dto.CreateProjectRequest;
import com.changelog.model.Project;
import com.changelog.repository.ProjectRepository;
import com.changelog.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID userId;
    private Project testProject;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        
        jdbcTemplate.update("INSERT INTO cc.tenants (id, name, slug, plan_tier) VALUES (?, 'Test Tenant', ?, 'startup')", tenantId, "test-tenant-" + tenantId);

        testProject = Project.builder()
            .tenantId(tenantId)
            .name("Test Project")
            .slug("test-project")
            .description("Test project for API validation")
            .branding(java.util.Map.of("primaryColor", "#0066FF"))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        projectRepository.save(testProject);
    }

    @Test
    void testGetAllProjects() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Test Project"));
    }

    @Test
    void testGetProjectById() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + testProject.getId())
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Test Project"))
            .andExpect(jsonPath("$.slug").value("test-project"));
    }

    @Test
    void testCreateProject() throws Exception {
        CreateProjectRequest newProject = new CreateProjectRequest();
        newProject.setName("New Project");
        newProject.setSlug("new-project");
        newProject.setDescription("Newly created project");

        mockMvc.perform(post("/api/v1/projects")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newProject)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("New Project"))
            .andExpect(jsonPath("$.tenantId").value(tenantId.toString()));
    }

    @Test
    void testTenantIsolation() throws Exception {
        UUID otherTenantId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/projects")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", otherTenantId.toString()).subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }
}