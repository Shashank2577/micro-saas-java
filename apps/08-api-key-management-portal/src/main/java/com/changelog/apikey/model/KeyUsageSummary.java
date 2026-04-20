package com.changelog.apikey.model;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "key_usage_summary")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(KeyUsageSummary.KeyUsageSummaryId.class)
public class KeyUsageSummary {

    @Id
    @Column(name = "key_id", nullable = false)
    private UUID keyId;

    @Id
    @Column(nullable = false)
    private String period; // day | week | month

    @Id
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "error_count", nullable = false)
    private long errorCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyUsageSummaryId implements Serializable {
        private UUID keyId;
        private String period;
        private LocalDate periodStart;
    }
}
