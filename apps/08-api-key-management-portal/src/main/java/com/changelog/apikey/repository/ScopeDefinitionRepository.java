package com.changelog.apikey.repository;

import com.changelog.apikey.model.ScopeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ScopeDefinitionRepository extends JpaRepository<ScopeDefinition, UUID> {
    List<ScopeDefinition> findByTenantId(UUID tenantId);
}
