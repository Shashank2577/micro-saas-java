package com.changelog.issuetracker.repository;

import com.changelog.issuetracker.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findAllByTenantId(UUID tenantId);
    Optional<Project> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Project> findByTenantIdAndSlug(UUID tenantId, String slug);
}
