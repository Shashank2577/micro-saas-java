package com.changelog.business.orchestration.event;

import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessEvent {

    private UUID id;
    private BusinessEventType type;
    private UUID tenantId;
    private UUID userId;
    private Map<String, Object> data;
    private Instant timestamp;
    private String correlationId;

    public enum BusinessEventType {
        // Customer Acquisition
        LANDING_PAGE_VIEWED,
        LANDING_PAGE_CONVERTED,
        EMAIL_OPENED,
        EMAIL_CLICKED,
        EMAIL_UNSUBSCRIBED,

        // Monetization
        TRIAL_STARTED,
        SUBSCRIPTION_CREATED,
        SUBSCRIPTION_UPDATED,
        SUBSCRIPTION_CANCELED,
        PAYMENT_SUCCEEDED,
        PAYMENT_FAILED,
        DUNNING_STARTED,
        DUNNING_RECOVERED,

        // Customer Success
        SUPPORT_TICKET_CREATED,
        SUPPORT_TICKET_RESOLVED,
        CHATBOT_CONVERSATION,
        CHATBOT_HANDOFF,
        NPS_SURVEY_RESPONSE,
        HEALTH_SCORE_CHANGED,
        CHURN_RISK_DETECTED,

        // Business Intelligence
        FUNNEL_STEP_COMPLETED,
        COHORT_ENTERED,
        MILESTONE_REACHED
    }

    @Transient
    public boolean isAcquisitionEvent() {
        return type.name().startsWith("LANDING_PAGE_") || type.name().startsWith("EMAIL_");
    }

    @Transient
    public boolean isMonetizationEvent() {
        return type.name().startsWith("TRIAL_") || type.name().startsWith("SUBSCRIPTION_") || type.name().startsWith("PAYMENT_");
    }

    @Transient
    public boolean isSuccessEvent() {
        return type.name().startsWith("SUPPORT_") || type.name().startsWith("CHATBOT_") || type.name().startsWith("HEALTH_");
    }

    @Transient
    public boolean isIntelligenceEvent() {
        return type.name().startsWith("FUNNEL_") || type.name().startsWith("COHORT_");
    }
}
