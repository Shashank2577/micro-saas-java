package com.changelog.okr.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "objectives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Objective {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private String level; // company | department | team | individual

    @Column(name = "owner_id")
    private UUID ownerId;

    private String department;

    @Column(nullable = false)
    private String status; // on_track | at_risk | off_track

    @Column(nullable = false)
    private BigDecimal progress;

    @Column(name = "final_score")
    private BigDecimal finalScore;

    @Column(name = "final_note")
    private String finalNote;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = "on_track";
        if (progress == null) progress = BigDecimal.ZERO;
        if (level == null) level = "team";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
