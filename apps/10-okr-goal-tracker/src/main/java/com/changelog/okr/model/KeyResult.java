package com.changelog.okr.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "key_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "objective_id", nullable = false)
    private UUID objectiveId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "kr_type", nullable = false)
    private String krType; // metric | milestone | percentage

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "start_value")
    private BigDecimal startValue;

    @Column(name = "target_value")
    private BigDecimal targetValue;

    @Column(name = "current_value")
    private BigDecimal currentValue;

    private String unit;

    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted;

    @Column(nullable = false)
    private BigDecimal progress;

    @Column(nullable = false)
    private String confidence; // on_track | at_risk | off_track

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (progress == null) progress = BigDecimal.ZERO;
        if (confidence == null) confidence = "on_track";
        if (krType == null) krType = "metric";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
