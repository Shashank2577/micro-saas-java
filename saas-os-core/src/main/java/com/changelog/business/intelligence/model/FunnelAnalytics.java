package com.changelog.business.intelligence.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "funnel_analytics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "funnel_name", nullable = false)
    private String funnelName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB", nullable = false)
    private List<Map<String, Object>> steps;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_counts", columnDefinition = "JSONB", nullable = false)
    private Map<String, Long> stepCounts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conversion_rates", columnDefinition = "JSONB", nullable = false)
    private Map<String, Double> conversionRates;

    @Column(name = "overall_conversion", columnDefinition = "NUMERIC")
    private Double overallConversion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dropoff_points", columnDefinition = "JSONB")
    private List<String> dropoffPoints;

    @Column(name = "bottleneck_step")
    private String bottleneckStep;

    @Column(name = "calculated_at", nullable = false)
    private java.time.LocalDateTime calculatedAt;
}
