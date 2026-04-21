package com.changelog.business.portals.repository;

import com.changelog.business.portals.model.Deliverable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliverableRepository extends JpaRepository<Deliverable, UUID> {
    List<Deliverable> findBySectionId(UUID sectionId);
}
