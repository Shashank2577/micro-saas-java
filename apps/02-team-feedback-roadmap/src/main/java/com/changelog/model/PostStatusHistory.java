package com.changelog.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_status_history")
@Getter
@Setter
public class PostStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private FeedbackPost post;

    @Column(name = "old_status")
    private String oldStatus;

    @NotBlank
    @Column(name = "new_status", nullable = false)
    private String newStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    private String note;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
