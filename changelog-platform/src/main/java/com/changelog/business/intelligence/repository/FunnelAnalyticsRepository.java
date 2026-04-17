package com.changelog.business.intelligence.repository;

import com.changelog.business.intelligence.model.FunnelAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface FunnelAnalyticsRepository extends JpaRepository<FunnelAnalytics, UUID> {

    List<FunnelAnalytics> findByTenantIdOrderByPeriodStartDesc(UUID tenantId);

    @Query("SELECT f FROM FunnelAnalytics f WHERE f.tenantId = :tenantId AND f.funnelName = :funnelName ORDER BY f.periodStart DESC")
    List<FunnelAnalytics> findByTenantIdAndFunnelNameOrderByPeriodStartDesc(UUID tenantId, String funnelName);

    @Query("SELECT f FROM FunnelAnalytics f WHERE f.tenantId = :tenantId AND f.periodStart >= :since AND f.periodEnd <= :end ORDER BY f.periodStart DESC")
    List<FunnelAnalytics> findByTenantIdAndPeriodRangeOrderByPeriodStartDesc(UUID tenantId, LocalDate since, LocalDate end);
}
