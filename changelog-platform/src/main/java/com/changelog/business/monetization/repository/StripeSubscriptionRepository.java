package com.changelog.business.monetization.repository;

import com.changelog.business.monetization.model.StripeSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StripeSubscriptionRepository extends JpaRepository<StripeSubscription, UUID> {

    Optional<StripeSubscription> findByStripeId(String stripeId);

    List<StripeSubscription> findByCustomerId(UUID customerId);

    @Query("SELECT s FROM StripeSubscription s WHERE s.customerId = :customerId AND s.status IN ('active', 'trialing')")
    List<StripeSubscription> findActiveByCustomerId(UUID customerId);

    @Query("SELECT s FROM StripeSubscription s WHERE s.tenantId = :tenantId AND s.status = 'active'")
    List<StripeSubscription> findActiveByTenantId(UUID tenantId);

    @Query("SELECT s FROM StripeSubscription s WHERE s.cancelAtPeriodEnd = true AND s.status = 'active' AND s.currentPeriodEnd <= :date")
    List<StripeSubscription> findScheduledCancellations(LocalDateTime date);
}
