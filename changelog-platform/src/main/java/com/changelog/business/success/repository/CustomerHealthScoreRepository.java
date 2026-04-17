package com.changelog.business.success.repository;

import com.changelog.business.success.model.CustomerHealthScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerHealthScoreRepository extends JpaRepository<CustomerHealthScore, UUID> {

    @Query("SELECT s FROM CustomerHealthScore s WHERE s.tenantId = :tenantId AND s.customerId = :customerId ORDER BY s.calculatedAt DESC LIMIT 1")
    Optional<CustomerHealthScore> findLatestByTenantAndCustomer(UUID tenantId, UUID customerId);

    @Query("SELECT s FROM CustomerHealthScore s WHERE s.tenantId = :tenantId AND s.riskLevel IN ('high', 'critical')")
    List<CustomerHealthScore> findHighRiskCustomers(UUID tenantId);

    @Query("SELECT s FROM CustomerHealthScore s WHERE s.tenantId = :tenantId AND s.score < :threshold")
    List<CustomerHealthScore> findByTenantIdAndScoreLessThan(UUID tenantId, Integer threshold);

    List<CustomerHealthScore> findByCustomerIdAndCalculatedAtAfter(UUID customerId, LocalDateTime date);
}
