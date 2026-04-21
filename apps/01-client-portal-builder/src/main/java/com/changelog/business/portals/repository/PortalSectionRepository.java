package com.changelog.business.portals.repository;

import com.changelog.business.portals.model.PortalSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortalSectionRepository extends JpaRepository<PortalSection, UUID> {
    List<PortalSection> findByPortalIdOrderByPositionAsc(UUID portalId);
}
