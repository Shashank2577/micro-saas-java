package com.changelog.okr.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "kr_check_ins", uniqueConstraints = {@UniqueConstraint(columnNames = {"key_result_id", "week_start"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KrCheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "key_result_id", nullable = false)
    private UUID keyResultId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "current_value")
    private BigDecimal currentValue;

    @Column(name = "is_completed")
    private Boolean isCompleted;

    @Column(name = "progress_pct", nullable = false)
    private BigDecimal progressPct;

    @Column(nullable = false)
    private String confidence;

    @Column(nullable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
