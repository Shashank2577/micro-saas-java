package com.changelog.repository;

import com.changelog.model.KbPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KbPageRepository extends JpaRepository<KbPage, UUID> {
    List<KbPage> findBySpaceIdAndTenantIdOrderByPositionAsc(UUID spaceId, UUID tenantId);
    Optional<KbPage> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(value = "SELECT * FROM kb_pages WHERE tenant_id = :tenantId AND content_tsv @@ plainto_tsquery('english', :query) ORDER BY ts_rank(content_tsv, plainto_tsquery('english', :query)) DESC", nativeQuery = true)
    List<KbPage> searchByKeyword(@Param("tenantId") UUID tenantId, @Param("query") String query);
}