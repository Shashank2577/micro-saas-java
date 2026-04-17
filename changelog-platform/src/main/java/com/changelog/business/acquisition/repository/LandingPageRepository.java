package com.changelog.business.acquisition.repository;

import com.changelog.business.acquisition.model.LandingPage;
import com.changelog.business.acquisition.model.LandingVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LandingPageRepository extends JpaRepository<LandingPage, UUID> {

    List<LandingPage> findByTenantId(UUID tenantId);

    Optional<LandingPage> findByTenantIdAndSlug(UUID tenantId, String slug);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    @Query("SELECT v FROM LandingVariant v WHERE v.pageId IN (SELECT id FROM LandingPage WHERE tenantId = :tenantId AND status = 'active')")
    List<LandingVariant> findActiveVariantsByTenantId(UUID tenantId);
}
