package com.changelog.repository;

import com.changelog.model.OnboardingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnboardingTemplateRepository extends JpaRepository<OnboardingTemplate, UUID> {
    List<OnboardingTemplate> findByTenantIdAndIsActiveTrue(UUID tenantId);
    Optional<OnboardingTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
}
