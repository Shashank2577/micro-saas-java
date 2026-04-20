package com.changelog.repository;

import com.changelog.model.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpaceRepository extends JpaRepository<Space, UUID> {
    List<Space> findByTenantId(UUID tenantId);
    Optional<Space> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);
}