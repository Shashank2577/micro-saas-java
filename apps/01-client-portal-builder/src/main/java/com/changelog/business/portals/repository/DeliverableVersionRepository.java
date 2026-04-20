package com.changelog.business.portals.repository;

import com.changelog.business.portals.model.DeliverableVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliverableVersionRepository extends JpaRepository<DeliverableVersion, UUID> {
    List<DeliverableVersion> findByDeliverableIdOrderByVersionNumberDesc(UUID deliverableId);
    Optional<DeliverableVersion> findByDeliverableIdAndVersionNumber(UUID deliverableId, Integer versionNumber);
}
