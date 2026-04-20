package com.changelog.business.portals.repository;

import com.changelog.business.portals.model.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
    List<Approval> findByDeliverableIdOrderByReviewedAtDesc(UUID deliverableId);
}
