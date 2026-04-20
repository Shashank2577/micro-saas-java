package com.changelog.business.portals.repository;

import com.changelog.business.portals.model.PortalMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortalMessageRepository extends JpaRepository<PortalMessage, UUID> {
    List<PortalMessage> findByPortalIdOrderByCreatedAtAsc(UUID portalId);
}
