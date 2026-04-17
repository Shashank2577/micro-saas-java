package com.changelog.business.intelligence.service;

import com.changelog.business.intelligence.model.AnalyticsEvent;
import com.changelog.business.intelligence.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final UnitEconomicsService unitEconomicsService;
    private final FunnelAnalyticsService funnelAnalyticsService;

    /**
     * Track a business event
     */
    @Transactional
    public void trackEvent(UUID tenantId, String eventName, Map<String, Object> properties) {
        trackEvent(tenantId, null, eventName, "business", null, properties);
    }

    /**
     * Track an event with user context
     */
    @Transactional
    public void trackEvent(UUID tenantId, UUID userId, String eventName, String eventCategory, Map<String, Object> properties) {
        trackEvent(tenantId, userId, eventName, "business", eventCategory, properties);
    }

    /**
     * Track an event
     */
    @Transactional
    public void trackEvent(UUID tenantId, UUID userId, String eventName, String eventType, String eventCategory, Map<String, Object> properties) {
        AnalyticsEvent event = AnalyticsEvent.builder()
                .tenantId(tenantId)
                .userId(userId)
                .eventName(eventName)
                .eventType(eventType)
                .eventCategory(eventCategory)
                .properties(properties != null ? properties : Map.of())
                .sessionId(UUID.randomUUID().toString())
                .occurredAt(Instant.now())
                .build();

        analyticsEventRepository.save(event);

        log.debug("Tracked event: tenant={}, event={}, user={}", tenantId, eventName, userId);
    }

    /**
     * Get event count for a tenant
     */
    public long getEventCount(UUID tenantId, String eventName, Instant since) {
        return analyticsEventRepository.countByTenantIdAndEventNameAndOccurredAtAfter(tenantId, eventName, since);
    }

    /**
     * Get unique users count for an event
     */
    public long getUniqueUserCount(UUID tenantId, String eventName, Instant since) {
        return analyticsEventRepository.countUniqueUsersByTenantIdAndEventNameAndOccurredAtAfter(tenantId, eventName, since);
    }

    /**
     * Scheduled job: Aggregate daily metrics
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void aggregateDailyMetrics() {
        log.info("Aggregating daily analytics metrics");

        LocalDate yesterday = LocalDate.now().minusDays(1);

        // Get all tenants with events
        Set<UUID> tenantIds = analyticsEventRepository.findDistinctTenantIds();

        for (UUID tenantId : tenantIds) {
            try {
                aggregateTenantDailyMetrics(tenantId, yesterday);
            } catch (Exception e) {
                log.error("Error aggregating metrics for tenant: {}", tenantId, e);
            }
        }

        log.info("Daily metrics aggregation complete");
    }

    /**
     * Aggregate metrics for a specific tenant
     */
    private void aggregateTenantDailyMetrics(UUID tenantId, LocalDate date) {
        // Aggregate by event name
        List<Object[]> eventCounts = analyticsEventRepository.countEventsByTenantAndDate(tenantId, date);

        for (Object[] row : eventCounts) {
            String eventName = (String) row[0];
            Long count = (Long) row[1];
            Long uniqueUsers = (Long) row[2];

            // TODO: Store in analytics_events_daily table
            log.info("Tenant: {}, Event: {}, Count: {}, Users: {}", tenantId, eventName, count, uniqueUsers);
        }
    }

    /**
     * Scheduled job: Calculate unit economics
     * Runs daily at midnight
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void calculateUnitEconomics() {
        log.info("Calculating unit economics for all tenants");

        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        lastMonth = lastMonth.withDayOfMonth(1); // First day of previous month

        Set<UUID> tenantIds = analyticsEventRepository.findDistinctTenantIds();

        for (UUID tenantId : tenantIds) {
            try {
                unitEconomicsService.calculateUnitEconomics(tenantId, lastMonth);
            } catch (Exception e) {
                log.error("Error calculating unit economics for tenant: {}", tenantId, e);
            }
        }

        log.info("Unit economics calculation complete");
    }

    /**
     * Get real-time dashboard metrics
     */
    public Map<String, Object> getDashboardMetrics(UUID tenantId) {
        Instant todayStart = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);

        Map<String, Object> metrics = new HashMap<>();

        // Acquisition metrics
        long landingPageViews = getEventCount(tenantId, "landing_page_viewed", todayStart);
        long conversions = getEventCount(tenantId, "landing_page_converted", todayStart);
        double conversionRate = landingPageViews > 0 ? (conversions * 100.0) / landingPageViews : 0;

        metrics.put("landingPageViews", landingPageViews);
        metrics.put("conversions", conversions);
        metrics.put("conversionRate", Math.round(conversionRate * 100.0) / 100.0);

        // Monetization metrics
        long subscriptions = getEventCount(tenantId, "subscription_created", todayStart);
        long paymentFailures = getEventCount(tenantId, "payment_failed", todayStart);

        metrics.put("newSubscriptions", subscriptions);
        metrics.put("paymentFailures", paymentFailures);

        // Success metrics
        long supportTickets = getEventCount(tenantId, "support_ticket_created", todayStart);
        long highRiskScores = getEventCount(tenantId, "health_score_changed", todayStart);

        metrics.put("supportTickets", supportTickets);
        metrics.put("highRiskCustomers", highRiskScores);

        // Unit economics (latest available)
        unitEconomicsService.getLatest(tenantId).ifPresent(unitEcon -> {
            metrics.put("mrr", unitEcon.getMrr());
            metrics.put("arr", unitEcon.getArr());
            metrics.put("totalCustomers", unitEcon.getTotalCustomers());
            metrics.put("ltvCacRatio", unitEcon.getLtvCacRatio());
            metrics.put("customerChurnRate", unitEcon.getCustomerChurnRate());
        });

        return metrics;
    }
}
