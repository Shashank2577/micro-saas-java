package com.changelog.apikey.repository;

import com.changelog.apikey.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByTenantId(UUID tenantId);
    List<ApiKey> findByTenantIdAndConsumerId(UUID tenantId, UUID consumerId);
    List<ApiKey> findByKeyPrefix(String keyPrefix);
}
