package com.changelog.business.intelligence.repository;

import com.changelog.business.intelligence.model.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    @Query("SELECT DISTINCT e.tenantId FROM AnalyticsEvent e WHERE e.occurredAt > :since")
    Set<UUID> findDistinctTenantIds();

    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.tenantId = :tenantId AND e.eventName = :eventName AND e.occurredAt > :since")
    long countByTenantIdAndEventNameAndOccurredAtAfter(UUID tenantId, String eventName, Instant since);

    @Query("SELECT COUNT(DISTINCT e.userId) FROM AnalyticsEvent e WHERE e.tenantId = :tenantId AND e.eventName = :eventName AND e.occurredAt > :since")
    long countUniqueUsersByTenantIdAndEventNameAndOccurredAtAfter(UUID tenantId, String eventName, Instant since);

    @Query("SELECT e.eventName, COUNT(*), COUNT(DISTINCT e.userId) FROM AnalyticsEvent e WHERE e.tenantId = :tenantId AND DATE(e.occurredAt) = :date GROUP BY e.eventName")
    List<Object[]> countEventsByTenantAndDate(UUID tenantId, java.time.LocalDate date);
}
