package com.changelog.issuetracker.controller;

import com.changelog.issuetracker.model.Issue;
import com.changelog.issuetracker.model.Project;
import com.changelog.issuetracker.repository.IssueRepository;
import com.changelog.config.LocalTenantResolver;
import com.changelog.issuetracker.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class IssueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId = LocalTenantResolver.DEV_TENANT_ID;
    private Project testProject;

    @BeforeEach
    void setUp() {
        issueRepository.deleteAll();
        projectRepository.deleteAll();

        testProject = Project.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Test Project")
                .slug("test-project")
                .status("ACTIVE")
                .createdBy(LocalTenantResolver.DEV_USER_ID)
                .build();
        projectRepository.save(testProject);
    }

    @Test
    @WithMockUser
    void testCreateAndGetIssue() throws Exception {
        Issue issue = Issue.builder()
                .title("Test Issue")
                .description("Test Description")
                .priority("HIGH")
                .status("OPEN")
                .build();

        String issueJson = objectMapper.writeValueAsString(issue);

        mockMvc.perform(post("/api/projects/" + testProject.getId() + "/issues")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Issue"))
                .andExpect(jsonPath("$.number").value(1));

        mockMvc.perform(get("/api/projects/" + testProject.getId() + "/issues")
                .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Test Issue"));
    }
}
