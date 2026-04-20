package com.changelog.apikey.repository;

import com.changelog.apikey.model.KeyAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KeyAuditEventRepository extends JpaRepository<KeyAuditEvent, UUID> {
    List<KeyAuditEvent> findByTenantId(UUID tenantId);
    List<KeyAuditEvent> findByKeyId(UUID keyId);
    List<KeyAuditEvent> findByConsumerId(UUID consumerId);
}
