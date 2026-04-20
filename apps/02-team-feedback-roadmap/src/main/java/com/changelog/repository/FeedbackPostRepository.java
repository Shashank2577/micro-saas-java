package com.changelog.repository;

import com.changelog.model.FeedbackPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeedbackPostRepository extends JpaRepository<FeedbackPost, UUID> {
    List<FeedbackPost> findByBoardIdAndTenantIdOrderByVoteCountDesc(UUID boardId, UUID tenantId);
    List<FeedbackPost> findByTenantIdAndIsPublicTrue(UUID tenantId);
    List<FeedbackPost> findByBoardIdAndTenantIdAndIsPublicTrueOrderByVoteCountDesc(UUID boardId, UUID tenantId);
    Optional<FeedbackPost> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<FeedbackPost> findByIdAndTenantIdAndIsPublicTrue(UUID id, UUID tenantId);
}
