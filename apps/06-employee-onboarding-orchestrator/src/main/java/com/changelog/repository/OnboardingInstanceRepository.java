package com.changelog.repository;

import com.changelog.model.OnboardingInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnboardingInstanceRepository extends JpaRepository<OnboardingInstance, UUID> {
    List<OnboardingInstance> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<OnboardingInstance> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<OnboardingInstance> findByPortalToken(String portalToken);
}
