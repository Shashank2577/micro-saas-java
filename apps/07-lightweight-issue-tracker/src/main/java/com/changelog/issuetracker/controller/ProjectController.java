package com.changelog.issuetracker.controller;

import com.changelog.config.TenantResolver;
import com.changelog.issuetracker.model.Project;
import com.changelog.issuetracker.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;
    private final TenantResolver tenantResolver;

    private UUID getTenantId(Jwt jwt) {
        return tenantResolver.getTenantId(jwt);
    }

    private UUID getUserId(Jwt jwt) {
        return tenantResolver.getUserId(jwt);
    }

    @GetMapping
    public List<Project> getAllProjects(@AuthenticationPrincipal Jwt jwt) {
        return projectService.getAllProjects(getTenantId(jwt));
    }

    @PostMapping
    public Project createProject(@AuthenticationPrincipal Jwt jwt, @RequestBody Project project) {
        project.setTenantId(getTenantId(jwt));
        project.setCreatedBy(getUserId(jwt));
        return projectService.createProject(project);
    }

    @GetMapping("/{projectId}")
    public Project getProject(@AuthenticationPrincipal Jwt jwt, @PathVariable("projectId") UUID projectId) {
        return projectService.getProject(projectId, getTenantId(jwt));
    }

    @PutMapping("/{projectId}")
    public Project updateProject(@AuthenticationPrincipal Jwt jwt, @PathVariable("projectId") UUID projectId, @RequestBody Project project) {
        return projectService.updateProject(projectId, project, getTenantId(jwt));
    }

    @DeleteMapping("/{projectId}")
    public void deleteProject(@AuthenticationPrincipal Jwt jwt, @PathVariable("projectId") UUID projectId) {
        projectService.deleteProject(projectId, getTenantId(jwt));
    }
}
