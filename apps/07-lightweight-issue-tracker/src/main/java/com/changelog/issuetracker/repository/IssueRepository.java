package com.changelog.issuetracker.repository;

import com.changelog.issuetracker.model.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID> {
    List<Issue> findAllByTenantIdAndProjectId(UUID tenantId, UUID projectId);
    Optional<Issue> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT i FROM Issue i WHERE i.tenantId = :tenantId AND (LOWER(i.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(i.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Issue> searchIssues(@Param("tenantId") UUID tenantId, @Param("query") String query);

    @Query("SELECT MAX(i.number) FROM Issue i WHERE i.project.id = :projectId")
    Long findMaxNumberByProjectId(@Param("projectId") UUID projectId);

    List<Issue> findAllByTenantId(UUID tenantId);
}
