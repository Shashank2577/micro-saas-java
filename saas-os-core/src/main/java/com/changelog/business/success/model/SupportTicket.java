package com.changelog.business.success.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private String priority = "normal"; // low | normal | high | urgent

    @Column(nullable = false)
    @Builder.Default
    private String status = "open"; // open | in_progress | waiting | resolved | closed

    @Column
    private String category; // AI-classified

    @Column
    private String sentiment; // AI-detected

    @Column(name = "sentiment_score", columnDefinition = "NUMERIC")
    private Double sentimentScore; // -1.0 to 1.0

    @Column(columnDefinition = "TEXT")
    private String suggestedReply;

    @Column(name = "suggested_category")
    private String suggestedCategory;

    @Column(name = "confidence_score", columnDefinition = "NUMERIC")
    private Double confidenceScore;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(nullable = false)
    private String channel; // email | chat | widget | api

    @Column(name = "source_url")
    private String sourceUrl;

    @Column
    private String resolution;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    @Column(name = "sla_breached")
    @Builder.Default
    private Boolean slaBreached = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Transient
    public boolean isOpen() {
        return "open".equals(status);
    }

    @Transient
    public boolean isResolved() {
        return "resolved".equals(status) || "closed".equals(status);
    }

    @Transient
    public boolean isUrgent() {
        return "urgent".equals(priority);
    }

    @Transient
    public boolean hasNegativeSentiment() {
        return sentimentScore != null && sentimentScore < -0.3;
    }
}
