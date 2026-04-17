package com.changelog.repository;

import com.changelog.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByTenantId(UUID tenantId);

    Optional<Project> findByTenantIdAndSlug(UUID tenantId, String slug);

    @Query("SELECT p FROM Project p WHERE p.slug = :slug AND p.tenantId IN (SELECT id FROM Tenant WHERE slug = :tenantSlug)")
    Optional<Project> findBySlugAndTenantSlug(@Param("slug") String slug, @Param("tenantSlug") String tenantSlug);

    Optional<Project> findBySlug(String slug);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);
}
