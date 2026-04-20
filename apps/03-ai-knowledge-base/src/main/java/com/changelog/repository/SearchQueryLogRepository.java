package com.changelog.repository;

import com.changelog.model.SearchQueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SearchQueryLogRepository extends JpaRepository<SearchQueryLog, UUID> {
    
    @Query(value = "SELECT query FROM search_query_log WHERE tenant_id = :tenantId AND result_count = 0 GROUP BY query ORDER BY count(*) DESC LIMIT 20", nativeQuery = true)
    List<String> findTopGaps(@Param("tenantId") UUID tenantId);
}