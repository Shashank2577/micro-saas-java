package com.changelog.service;

import com.changelog.dto.CreateProjectRequest;
import com.changelog.dto.ProjectResponse;
import com.changelog.model.Project;
import com.changelog.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public List<ProjectResponse> getAllProjects(UUID tenantId) {
        return projectRepository.findByTenantId(tenantId).stream()
                .map(ProjectResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public ProjectResponse getProject(UUID tenantId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        if (!project.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }

        return ProjectResponse.fromEntity(project);
    }

    public ProjectResponse getProjectBySlug(String tenantSlug, String projectSlug) {
        return projectRepository.findBySlugAndTenantSlug(projectSlug, tenantSlug)
                .map(ProjectResponse::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
    }

    public ProjectResponse getProjectBySlug(String slug) {
        return projectRepository.findBySlug(slug)
                .map(ProjectResponse::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
    }

    @Transactional
    public ProjectResponse createProject(UUID tenantId, CreateProjectRequest request) {
        if (projectRepository.existsByTenantIdAndSlug(tenantId, request.getSlug())) {
            throw new IllegalArgumentException("Project slug already exists");
        }

        Project project = Project.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .branding(request.getBranding() != null ? request.getBranding() : Map.of())
                .build();

        Project saved = projectRepository.save(project);
        return ProjectResponse.fromEntity(saved);
    }

    @Transactional
    public ProjectResponse updateProject(UUID tenantId, UUID projectId, CreateProjectRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        if (!project.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }

        if (!project.getSlug().equals(request.getSlug()) &&
                projectRepository.existsByTenantIdAndSlug(tenantId, request.getSlug())) {
            throw new IllegalArgumentException("Project slug already exists");
        }

        project.setName(request.getName());
        project.setSlug(request.getSlug());
        project.setDescription(request.getDescription());
        if (request.getBranding() != null) {
            project.setBranding(request.getBranding());
        }

        Project updated = projectRepository.save(project);
        return ProjectResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteProject(UUID tenantId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        if (!project.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }

        projectRepository.delete(project);
    }
}
