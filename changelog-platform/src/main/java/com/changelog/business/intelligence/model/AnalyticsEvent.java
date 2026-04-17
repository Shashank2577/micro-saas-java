package com.changelog.business.intelligence.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "analytics_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "event_type", nullable = false)
    private String eventType; // business | technical

    @Column(name = "event_category")
    private String eventCategory; // acquisition | monetization | retention | referral

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB", nullable = false)
    private Map<String, Object> properties;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column
    private String referrer;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Transient
    public boolean isAcquisitionEvent() {
        return "acquisition".equals(eventCategory);
    }

    @Transient
    public boolean isMonetizationEvent() {
        return "monetization".equals(eventCategory);
    }

    @Transient
    public boolean isRetentionEvent() {
        return "retention".equals(eventCategory);
    }
}
