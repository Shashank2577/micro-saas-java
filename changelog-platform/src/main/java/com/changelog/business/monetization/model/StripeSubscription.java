package com.changelog.business.monetization.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "stripe_subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StripeSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "stripe_id", nullable = false, unique = true)
    private String stripeId;

    @Column(name = "stripe_product_id", nullable = false)
    private UUID stripeProductId;

    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Column(nullable = false)
    private String status; // active | canceled | past_due | trialing | incomplete

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "cancel_at_period_end")
    @Builder.Default
    private Boolean cancelAtPeriodEnd = false;

    @Column(name = "cancel_at")
    private LocalDateTime cancelAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "trial_start")
    private LocalDateTime trialStart;

    @Column(name = "trial_end")
    private LocalDateTime trialEnd;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private Map<String, Object> metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Transient
    public boolean isActive() {
        return "active".equals(status) || "trialing".equals(status);
    }

    @Transient
    public boolean isCanceled() {
        return "canceled".equals(status);
    }

    @Transient
    public boolean isInTrial() {
        return "trialing".equals(status) && trialEnd != null && trialEnd.isAfter(LocalDateTime.now());
    }

    @Transient
    public boolean willCancelAtPeriodEnd() {
        return cancelAtPeriodEnd && isActive();
    }
}
