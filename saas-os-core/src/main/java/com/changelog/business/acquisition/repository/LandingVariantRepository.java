package com.changelog.business.acquisition.repository;

import com.changelog.business.acquisition.model.LandingVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LandingVariantRepository extends JpaRepository<LandingVariant, UUID> {

    Optional<LandingVariant> findByIdAndTenantId(UUID id, UUID tenantId);

    List<LandingVariant> findByPageId(UUID pageId);

    List<LandingVariant> findByTenantId(UUID tenantId);

    @Query("SELECT v FROM LandingVariant v WHERE v.pageId = :pageId AND v.status = 'active'")
    List<LandingVariant> findActiveByPageId(UUID pageId);
}
