package com.changelog.business.intelligence.service;

import com.changelog.business.intelligence.model.FunnelAnalytics;
import com.changelog.business.intelligence.repository.AnalyticsEventRepository;
import com.changelog.business.intelligence.repository.FunnelAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FunnelAnalyticsService {

    private final FunnelAnalyticsRepository funnelAnalyticsRepository;
    private final AnalyticsEventRepository analyticsEventRepository;

    /**
     * Get the latest funnel calculation
     */
    public Optional<FunnelAnalytics> getLatest(UUID tenantId, String funnelName) {
        List<FunnelAnalytics> funnels = funnelAnalyticsRepository.findByTenantIdAndFunnelNameOrderByPeriodStartDesc(tenantId, funnelName);
        return funnels.isEmpty() ? Optional.empty() : Optional.of(funnels.get(0));
    }

    /**
     * Get all funnels for a tenant
     */
    public List<FunnelAnalytics> getAllFunnels(UUID tenantId) {
        return funnelAnalyticsRepository.findByTenantIdOrderByPeriodStartDesc(tenantId);
    }

    /**
     * Get funnel history by name
     */
    public List<FunnelAnalytics> getFunnel(UUID tenantId, String funnelName) {
        return funnelAnalyticsRepository.findByTenantIdAndFunnelNameOrderByPeriodStartDesc(tenantId, funnelName);
    }

    /**
     * Define a funnel and calculate its performance
     */
    @Transactional
    public void calculateFunnel(UUID tenantId, String funnelName, List<FunnelStep> steps, LocalDate startDate, LocalDate endDate) {
        log.info("Calculating funnel: tenant={}, funnel={}, steps={}", tenantId, funnelName, steps.size());

        Map<String, Long> stepCounts = new LinkedHashMap<>();
        Map<String, Double> conversionRates = new LinkedHashMap<>();

        // Calculate count for each step
        for (int i = 0; i < steps.size(); i++) {
            FunnelStep step = steps.get(i);
            long count = getEventCountForStep(tenantId, step, startDate, endDate);
            stepCounts.put(step.getName(), count);

            // Calculate conversion rate from previous step
            if (i > 0) {
                FunnelStep previousStep = steps.get(i - 1);
                long previousCount = stepCounts.get(previousStep.getName());
                double rate = previousCount > 0 ? (count * 100.0) / previousCount : 0;
                conversionRates.put(previousStep.getName() + "_to_" + step.getName(), Math.round(rate * 100.0) / 100.0);
            }
        }

        // Calculate overall conversion rate
        long firstStepCount = stepCounts.get(steps.get(0).getName());
        long lastStepCount = stepCounts.get(steps.get(steps.size() - 1).getName());
        double overallConversion = firstStepCount > 0 ? (lastStepCount * 100.0) / firstStepCount : 0;

        // Find bottleneck (worst performing step)
        String bottleneckStep = findBottleneckStep(conversionRates);

        // Create funnel analytics record
        FunnelAnalytics funnel = FunnelAnalytics.builder()
                .tenantId(tenantId)
                .funnelName(funnelName)
                .steps(serializeSteps(steps))
                .periodStart(startDate)
                .periodEnd(endDate)
                .stepCounts(stepCounts)
                .conversionRates(conversionRates)
                .overallConversion(BigDecimal.valueOf(overallConversion).setScale(2, RoundingMode.HALF_UP).doubleValue())
                .dropoffPoints(identifyDropoffPoints(conversionRates))
                .bottleneckStep(bottleneckStep)
                .build();

        funnelAnalyticsRepository.save(funnel);

        log.info("Funnel calculated: tenant={}, funnel={}, overallConversion={}%, bottleneck={}",
                tenantId, funnelName, overallConversion, bottleneckStep);
    }

    /**
     * Get event count for a funnel step
     */
    private long getEventCountForStep(UUID tenantId, FunnelStep step, LocalDate startDate, LocalDate endDate) {
        return analyticsEventRepository.countByTenantIdAndEventNameAndOccurredAtAfter(
                tenantId,
                step.getEventName(),
                startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        );
    }

    /**
     * Find the bottleneck step (worst conversion rate)
     */
    private String findBottleneckStep(Map<String, Double> conversionRates) {
        return conversionRates.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(entry -> {
                    // Extract step name from "step1_to_step2"
                    String[] parts = entry.getKey().split("_to_");
                    return parts.length > 0 ? parts[0] : entry.getKey();
                })
                .orElse(null);
    }

    /**
     * Identify steps with high dropoff (> 50% drop)
     */
    private List<String> identifyDropoffPoints(Map<String, Double> conversionRates) {
        List<String> dropoffs = new ArrayList<>();

        conversionRates.forEach((step, rate) -> {
            if (rate < 50.0) { // Less than 50% conversion
                dropoffs.add(step);
            }
        });

        return dropoffs;
    }

    /**
     * Serialize steps to JSON
     */
    private List<Map<String, Object>> serializeSteps(List<FunnelStep> steps) {
        List<Map<String, Object>> serialized = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            FunnelStep step = steps.get(i);
            Map<String, Object> stepMap = new HashMap<>();
            stepMap.put("step", i + 1);
            stepMap.put("name", step.getName());
            stepMap.put("event_name", step.getEventName());
            serialized.add(stepMap);
        }

        return serialized;
    }

    /**
     * Funnel step definition
     */
    public static class FunnelStep {
        private final String name;
        private final String eventName;

        public FunnelStep(String name, String eventName) {
            this.name = name;
            this.eventName = eventName;
        }

        public String getName() {
            return name;
        }

        public String getEventName() {
            return eventName;
        }
    }

    /**
     * Get standard funnel definitions
     */
    public static class StandardFunnels {

        public static List<FunnelStep> freeToPaid() {
            return Arrays.asList(
                    new FunnelStep("Visited Pricing", "pricing_page_viewed"),
                    new FunnelStep("Started Trial", "trial_started"),
                    new FunnelStep("Added Payment Method", "payment_method_added"),
                    new FunnelStep("Subscribed", "subscription_created")
            );
        }

        public static List<FunnelStep> onboarding() {
            return Arrays.asList(
                    new FunnelStep("Signed Up", "user_signup"),
                    new FunnelStep("Completed Profile", "profile_completed"),
                    new FunnelStep("Created First Project", "first_project_created"),
                    new FunnelStep("Active Usage", "feature_used")
            );
        }

        public static List<FunnelStep> activation() {
            return Arrays.asList(
                    new FunnelStep("Subscribed", "subscription_created"),
                    new FunnelStep("Logged In", "user_login"),
                    new FunnelStep("Used Core Feature", "core_feature_used"),
                    new FunnelStep("Activated", "user_activated")
            );
        }
    }
}
