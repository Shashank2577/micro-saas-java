package com.changelog.business.success.service;

import com.changelog.business.orchestration.event.BusinessEvent;
import com.changelog.business.success.model.CustomerHealthScore;
import com.changelog.business.success.repository.CustomerHealthScoreRepository;
import com.changelog.business.orchestration.service.BusinessEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerHealthScoringService {

    private final CustomerHealthScoreRepository healthScoreRepository;
    private final BusinessEventPublisher eventPublisher;

    /**
     * Calculate health score for a customer
     * Called on: signup, subscription change, usage change, support ticket, login
     */
    @Transactional
    public void calculateHealthScore(UUID tenantId, UUID customerId) {
        List<CustomerHealthScore.HealthSignal> signals = analyzeCustomerSignals(tenantId, customerId);

        int score = calculateScoreFromSignals(signals);
        String riskLevel = determineRiskLevel(score);
        List<String> actions = generateRecommendedActions(signals, score);

        // Get previous score for trend
        Integer previousScore = healthScoreRepository
                .findLatestByTenantAndCustomer(tenantId, customerId)
                .map(CustomerHealthScore::getScore)
                .orElse(null);

        String trend = determineTrend(previousScore, score);

        CustomerHealthScore healthScore = CustomerHealthScore.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .score(score)
                .riskLevel(riskLevel)
                .signals(null)
                .recommendedActions(actions)
                .actionConfidence(calculateActionConfidence(signals))
                .previousScore(previousScore)
                .scoreTrend(trend)
                .calculatedAt(LocalDateTime.now())
                .build();

        // Set signals separately to avoid JSONB conversion issues
        healthScore.setSignals(signals);

        healthScoreRepository.save(healthScore);

        // Publish event if risk level changed
        if (!riskLevel.equals("low") && (previousScore == null || score < previousScore)) {
            eventPublisher.publish(
                    BusinessEvent.BusinessEventType.HEALTH_SCORE_CHANGED,
                    tenantId,
                    customerId,
                    Map.of(
                            "newScore", score,
                            "previousScore", previousScore,
                            "riskLevel", riskLevel,
                            "recommendedActions", actions
                    )
            );

            // If critical or high risk, publish churn risk event
            if (riskLevel.equals("critical") || riskLevel.equals("high")) {
                eventPublisher.publish(
                        BusinessEvent.BusinessEventType.CHURN_RISK_DETECTED,
                        tenantId,
                        customerId,
                        Map.of(
                                "riskLevel", riskLevel,
                                "score", score,
                                "recommendedActions", actions
                        )
                );
            }
        }
    }

    /**
     * Analyze customer to generate health signals
     */
    private List<CustomerHealthScore.HealthSignal> analyzeCustomerSignals(UUID tenantId, UUID customerId) {
        List<CustomerHealthScore.HealthSignal> signals = new ArrayList<>();

        // Signal 1: Login frequency
        signals.add(analyzeLoginFrequency(tenantId, customerId));

        // Signal 2: Usage patterns
        signals.add(analyzeUsagePatterns(tenantId, customerId));

        // Signal 3: Support tickets
        signals.add(analyzeSupportTickets(tenantId, customerId));

        // Signal 4: Payment history
        signals.add(analyzePaymentHistory(tenantId, customerId));

        // Signal 5: Subscription age
        signals.add(analyzeSubscriptionAge(tenantId, customerId));

        // Signal 6: Feature adoption
        signals.add(analyzeFeatureAdoption(tenantId, customerId));

        return signals;
    }

    /**
     * Calculate overall score from signals
     */
    private int calculateScoreFromSignals(List<CustomerHealthScore.HealthSignal> signals) {
        int score = 100;

        for (CustomerHealthScore.HealthSignal signal : signals) {
            score += signal.getImpact();
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Determine risk level from score
     */
    private String determineRiskLevel(int score) {
        if (score >= 80) return "low";
        if (score >= 60) return "medium";
        if (score >= 40) return "high";
        return "critical";
    }

    /**
     * Generate recommended actions based on signals and score
     */
    private List<String> generateRecommendedActions(List<CustomerHealthScore.HealthSignal> signals, int score) {
        List<String> actions = new ArrayList<>();

        if (score < 40) {
            actions.addAll(Arrays.asList(
                    "send_20_percent_off",
                    "schedule_ceo_call",
                    "create_retention_ticket"
            ));
        } else if (score < 60) {
            actions.addAll(Arrays.asList(
                    "send_checkin_email",
                    "offer_extended_trial",
                    "schedule_success_call"
            ));
        } else if (score < 80) {
            actions.addAll(Arrays.asList(
                    "send_feature_tips",
                    "offer_upgrade_incentive"
            ));
        }

        // Add specific actions based on signals
        for (CustomerHealthScore.HealthSignal signal : signals) {
            if (signal.getImpact() < -20) {
                switch (signal.getType()) {
                    case "login_frequency":
                        actions.add("send_reengagement_email");
                        break;
                    case "support_tickets":
                        actions.add("resolve_outstanding_tickets");
                        break;
                    case "payment_failed":
                        actions.add("update_payment_method");
                        break;
                }
            }
        }

        return actions;
    }

    /**
     * Calculate confidence in recommended actions (0-1)
     */
    private Double calculateActionConfidence(List<CustomerHealthScore.HealthSignal> signals) {
        // More signals = higher confidence
        // Stronger signals (more negative impact) = higher confidence
        int totalImpact = signals.stream().mapToInt(CustomerHealthScore.HealthSignal::getImpact).sum();

        if (totalImpact == 0) return 0.5;
        if (totalImpact < -30) return 0.9;
        if (totalImpact < -20) return 0.8;
        if (totalImpact < -10) return 0.7;
        return 0.6;
    }

    /**
     * Determine trend from previous to current score
     */
    private String determineTrend(Integer previous, Integer current) {
        if (previous == null) return "stable";
        if (current > previous + 10) return "improving";
        if (current < previous - 10) return "declining";
        return "stable";
    }

    // Signal analysis methods (TODO: Implement with real data)

    private CustomerHealthScore.HealthSignal analyzeLoginFrequency(UUID tenantId, UUID customerId) {
        // TODO: Query analytics_events for login events
        // For now, return a dummy signal
        return new CustomerHealthScore.HealthSignal("login_frequency", 0, 0, "Regular login activity");
    }

    private CustomerHealthScore.HealthSignal analyzeUsagePatterns(UUID tenantId, UUID customerId) {
        // TODO: Query usage data, compare to previous period
        return new CustomerHealthScore.HealthSignal("usage_decline", null, -15, "Slight drop in usage");
    }

    private CustomerHealthScore.HealthSignal analyzeSupportTickets(UUID tenantId, UUID customerId) {
        // TODO: Query support_tickets for this customer
        return new CustomerHealthScore.HealthSignal("support_tickets", null, 0, "No unresolved tickets");
    }

    private CustomerHealthScore.HealthSignal analyzePaymentHistory(UUID tenantId, UUID customerId) {
        // TODO: Check for recent payment failures
        return new CustomerHealthScore.HealthSignal("payment_failed", null, -10, "One recent payment failure");
    }

    private CustomerHealthScore.HealthSignal analyzeSubscriptionAge(UUID tenantId, UUID customerId) {
        // TODO: Calculate subscription age
        return new CustomerHealthScore.HealthSignal("subscription_age", null, 5, "Long-term customer");
    }

    private CustomerHealthScore.HealthSignal analyzeFeatureAdoption(UUID tenantId, UUID customerId) {
        // TODO: Check which features are being used
        return new CustomerHealthScore.HealthSignal("feature_adoption", null, 10, "Good feature adoption");
    }

    /**
     * Scheduled job: Recalculate health scores for all customers
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    public void recalculateAllHealthScores() {
        log.info("Recalculating health scores for all customers");

        // TODO: Get all active customers from Stripe subscriptions
        // For each, call calculateHealthScore()

        log.info("Health score recalculation complete");
    }
}
