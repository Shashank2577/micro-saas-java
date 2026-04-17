package com.changelog.business.intelligence.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "unit_economics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitEconomics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private LocalDate period; // month (first day)

    // Revenue metrics
    @Column(name = "mrr", nullable = false)
    private BigDecimal mrr; // Monthly Recurring Revenue

    @Column(name = "arr", nullable = false)
    private BigDecimal arr; // Annual Recurring Revenue

    @Column(name = "new_mrr", nullable = false)
    private BigDecimal newMrr; // New customer MRR

    @Column(name = "expansion_mrr", nullable = false)
    private BigDecimal expansionMrr; // Upsell/cross-sell MRR

    @Column(name = "churn_mrr", nullable = false)
    private BigDecimal churnMrr; // Lost MRR from churn

    @Column(name = "reactivation_mrr")
    private BigDecimal reactivationMrr; // Recovered MRR from churned customers

    // Customer metrics
    @Column(name = "total_customers", nullable = false)
    private Integer totalCustomers;

    @Column(name = "new_customers", nullable = false)
    private Integer newCustomers;

    @Column(name = "churned_customers", nullable = false)
    private Integer churnedCustomers;

    // Unit economics
    @Column
    private BigDecimal cac; // Customer Acquisition Cost

    @Column
    private BigDecimal ltv; // Lifetime Value

    @Column(name = "ltv_cac_ratio")
    private BigDecimal ltvCacRatio; // LTV:CAC (ideal > 3)

    @Column(name = "payback_period")
    private Integer paybackPeriod; // months to recover CAC

    // Churn metrics
    @Column(name = "customer_churn_rate")
    private BigDecimal customerChurnRate; // percentage

    @Column(name = "revenue_churn_rate")
    private BigDecimal revenueChurnRate; // percentage

    @CreatedDate
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Transient
    public boolean hasHealthyLtvCacRatio() {
        return ltvCacRatio != null && ltvCacRatio.compareTo(new BigDecimal("3")) >= 0;
    }

    @Transient
    public int getPaybackPeriodMonths() {
        return paybackPeriod != null ? paybackPeriod : 0;
    }

    @Transient
    public boolean hasAcceptableChurn() {
        BigDecimal threshold = new BigDecimal("0.05"); // 5% monthly churn
        return customerChurnRate != null && customerChurnRate.compareTo(threshold) <= 0;
    }
}
