package com.changelog.apikey.repository;

import com.changelog.apikey.model.ApiConsumer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiConsumerRepository extends JpaRepository<ApiConsumer, UUID> {
    List<ApiConsumer> findByTenantId(UUID tenantId);
    Optional<ApiConsumer> findByTenantIdAndExternalId(UUID tenantId, String externalId);
}
