package com.changelog.business.intelligence.service;

import com.changelog.business.intelligence.model.UnitEconomics;
import com.changelog.business.intelligence.repository.UnitEconomicsRepository;
import com.changelog.business.monetization.repository.StripeSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class UnitEconomicsService {

    private final UnitEconomicsRepository unitEconomicsRepository;
    private final StripeSubscriptionRepository subscriptionRepository;

    /**
     * Calculate unit economics for a tenant for a specific period
     */
    @Transactional
    public void calculateUnitEconomics(UUID tenantId, LocalDate period) {
        log.info("Calculating unit economics: tenant={}, period={}", tenantId, period);

        // Get active subscriptions for the period
        var subscriptions = subscriptionRepository.findActiveByTenantId(tenantId);

        // Calculate MRR
        BigDecimal mrr = subscriptions.stream()
                .map(sub -> {
                    // TODO: Get price from StripeProduct
                    // For now, assume $29/month average
                    return new BigDecimal("29.00");
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal arr = mrr.multiply(BigDecimal.valueOf(12));

        // Customer counts
        int totalCustomers = subscriptions.size();

        // New customers (TODO: from new subscriptions in period)
        int newCustomers = 0; // Placeholder
        BigDecimal newMrr = new BigDecimal("0.00"); // Placeholder

        // Churned customers (TODO: from canceled subscriptions)
        int churnedCustomers = 0; // Placeholder
        BigDecimal churnMrr = new BigDecimal("0.00"); // Placeholder

        // Placeholder for other metrics
        BigDecimal expansionMrr = new BigDecimal("0.00");
        BigDecimal reactivationMrr = new BigDecimal("0.00");
        BigDecimal cac = new BigDecimal("150.00"); // Industry average placeholder
        BigDecimal ltv = new BigDecimal("450.00"); // 3x CAC placeholder
        BigDecimal ltvCacRatio = ltv.divide(cac, 2, RoundingMode.HALF_UP);
        int paybackPeriod = 6; // months

        // Churn rates
        BigDecimal customerChurnRate = totalCustomers > 0
                ? BigDecimal.valueOf((double) churnedCustomers / totalCustomers * 100)
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal revenueChurnRate = mrr.compareTo(BigDecimal.ZERO) > 0
                ? churnMrr.divide(mrr, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        UnitEconomics unitEcon = UnitEconomics.builder()
                .tenantId(tenantId)
                .period(period)
                .mrr(mrr)
                .arr(arr)
                .newMrr(newMrr)
                .expansionMrr(expansionMrr)
                .churnMrr(churnMrr)
                .reactivationMrr(reactivationMrr)
                .totalCustomers(totalCustomers)
                .newCustomers(newCustomers)
                .churnedCustomers(churnedCustomers)
                .cac(cac)
                .ltv(ltv)
                .ltvCacRatio(ltvCacRatio)
                .paybackPeriod(paybackPeriod)
                .customerChurnRate(customerChurnRate)
                .revenueChurnRate(revenueChurnRate)
                .build();

        unitEconomicsRepository.save(unitEcon);

        log.info("Unit economics calculated: tenant={}, mrr={}, customers={}, churnRate={}",
                tenantId, mrr, totalCustomers, customerChurnRate);
    }

    /**
     * Get latest unit economics for a tenant
     */
    public Optional<UnitEconomics> getLatest(UUID tenantId) {
        return unitEconomicsRepository.findFirstByTenantIdOrderByPeriodDesc(tenantId);
    }

    /**
     * Get unit economics history for a tenant
     */
    public List<UnitEconomics> getHistory(UUID tenantId, int months) {
        return unitEconomicsRepository.findByTenantIdOrderByPeriodDesc(tenantId, LocalDate.now().minusMonths(months));
    }

    /**
     * Calculate LTV:CAC ratio health
     */
    public String getLtvCacHealthStatus(UUID tenantId) {
        return getLatest(tenantId)
                .map(unitEcon -> {
                    if (unitEcon.getLtvCacRatio() == null) {
                        return "unknown";
                    }
                    BigDecimal ratio = unitEcon.getLtvCacRatio();
                    if (ratio.compareTo(new BigDecimal("5")) >= 0) {
                        return "excellent"; // > 5:1
                    } else if (ratio.compareTo(new BigDecimal("3")) >= 0) {
                        return "healthy"; // 3:1 to 5:1
                    } else if (ratio.compareTo(new BigDecimal("1")) >= 0) {
                        return "warning"; // 1:1 to 3:1
                    } else {
                        return "critical"; // < 1:1
                    }
                })
                .orElse("unknown");
    }
}
