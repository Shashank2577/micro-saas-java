package com.changelog.business.portals.repository;

import com.changelog.business.portals.model.Portal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortalRepository extends JpaRepository<Portal, UUID> {
    List<Portal> findByTenantId(UUID tenantId);
    Optional<Portal> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);
}
