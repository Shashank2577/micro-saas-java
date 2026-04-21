package com.changelog.apikey.repository;

import com.changelog.apikey.model.KeyUsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KeyUsageEventRepository extends JpaRepository<KeyUsageEvent, UUID> {
    List<KeyUsageEvent> findByTenantId(UUID tenantId);
    List<KeyUsageEvent> findByKeyId(UUID keyId);
}
