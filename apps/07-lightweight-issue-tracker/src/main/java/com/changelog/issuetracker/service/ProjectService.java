package com.changelog.issuetracker.service;

import com.changelog.issuetracker.model.Project;
import com.changelog.issuetracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;

    public List<Project> getAllProjects(UUID tenantId) {
        return projectRepository.findAllByTenantId(tenantId);
    }

    public Project getProject(UUID id, UUID tenantId) {
        return projectRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
    }

    @Transactional
    public Project createProject(Project project) {
        if (project.getId() == null) {
            project.setId(UUID.randomUUID());
        }
        return projectRepository.save(project);
    }

    @Transactional
    public Project updateProject(UUID id, Project projectDetails, UUID tenantId) {
        Project project = getProject(id, tenantId);
        project.setName(projectDetails.getName());
        project.setDescription(projectDetails.getDescription());
        project.setStatus(projectDetails.getStatus());
        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(UUID id, UUID tenantId) {
        Project project = getProject(id, tenantId);
        projectRepository.delete(project);
    }
}
