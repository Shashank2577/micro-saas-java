package com.changelog.business.success.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_health_scores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerHealthScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private Integer score; // 0-100

    @Column(nullable = false)
    private String riskLevel; // low | medium | high | critical

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB", nullable = false)
    private List<HealthSignal> signals;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private List<String> recommendedActions;

    @Column(name = "action_confidence", columnDefinition = "NUMERIC")
    private Double actionConfidence;

    @Column
    private Integer previousScore;

    @Column(name = "score_trend")
    private String scoreTrend; // improving | stable | declining

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HealthSignal {
        private String type;
        private Object value;
        private Integer impact;
        private String description;
    }

    @Transient
    public boolean isCriticalRisk() {
        return "critical".equals(riskLevel);
    }

    @Transient
    public boolean isHighRisk() {
        return "high".equals(riskLevel) || isCriticalRisk();
    }

    @Transient
    public boolean needsIntervention() {
        return isHighRisk() && (recommendedActions == null || !recommendedActions.isEmpty());
    }
}
