package com.changelog.service;

import com.changelog.dto.CreateProjectRequest;
import com.changelog.dto.ProjectMapper;
import com.changelog.dto.ProjectResponse;
import com.changelog.model.Project;
import com.changelog.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final SlugService slugService;

    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    public List<ProjectResponse> getAllProjects(UUID tenantId) {
        return projectRepository.findByTenantId(tenantId).stream()
                .map(projectMapper::toResponse)
                .collect(Collectors.toList());
    }

    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    public ProjectResponse getProject(UUID tenantId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        return projectMapper.toResponse(project);
    }

    public ProjectResponse getProjectBySlug(String tenantSlug, String projectSlug) {
        return projectRepository.findBySlugAndTenantSlug(projectSlug, tenantSlug)
                .map(projectMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
    }

    public ProjectResponse getProjectBySlug(String slug) {
        return projectRepository.findBySlug(slug)
                .map(projectMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
    }

    @Transactional
    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    public ProjectResponse createProject(UUID tenantId, CreateProjectRequest request) {
        String slug = (request.getSlug() == null || request.getSlug().isBlank()) 
                ? slugService.slugify(request.getName()) 
                : slugService.slugify(request.getSlug());

        if (projectRepository.existsByTenantIdAndSlug(tenantId, slug)) {
            throw new IllegalArgumentException("Project slug already exists: " + slug);
        }

        Project project = projectMapper.toEntity(request);
        project.setTenantId(tenantId);
        project.setSlug(slug);

        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(saved);
    }

    @Transactional
    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    public ProjectResponse updateProject(UUID tenantId, UUID projectId, CreateProjectRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        String newSlug = (request.getSlug() == null || request.getSlug().isBlank())
                ? slugService.slugify(request.getName())
                : slugService.slugify(request.getSlug());

        if (!project.getSlug().equals(newSlug) &&
                projectRepository.existsByTenantIdAndSlug(tenantId, newSlug)) {
            throw new IllegalArgumentException("Project slug already exists: " + newSlug);
        }

        projectMapper.updateEntity(request, project);
        project.setSlug(newSlug);

        Project updated = projectRepository.save(project);
        return projectMapper.toResponse(updated);
    }

    @Transactional
    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    public void deleteProject(UUID tenantId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        projectRepository.delete(project);
    }
}
