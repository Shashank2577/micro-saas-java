package com.changelog.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feedback_posts")
@Getter
@Setter
public class FeedbackPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Column(nullable = false)
    private String title;

    private String description;

    @NotBlank
    @Column(nullable = false)
    private String status = "under_review"; // under_review | planned | in_progress | completed | declined

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitter_email")
    private String submitterEmail;

    @Column(name = "submitter_name")
    private String submitterName;

    @Column(name = "vote_count", nullable = false)
    private int voteCount = 0;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Column(name = "eta_label")
    private String etaLabel;

    // pgvector is generally mapped as float[] or String in simpler cases, we can use an array mapping.
    // For MVP, we'll map it to a double[] or String since JPA 3 / Hibernate 6 has vector support via dialects, but we need to keep it simple.
    // Given the memory mentions "In JPA entities, map Strings to PostgreSQL jsonb columns using both @JdbcTypeCode(SqlTypes.JSON) and @Column(columnDefinition = "jsonb")."
    // Wait, embedding is vector(1536), not jsonb.
    // We'll leave it out for MVP unless needed, or map it as string. Let's just comment it out to avoid type issues unless necessary for MVP features.
    // Actually, let's map it as a string to avoid dialect issues if pgvector isn't fully configured in the datasource.
    // Or better yet, we can use float[] if the driver supports it.
    @Column(name = "embedding", columnDefinition = "vector")
    private String embedding;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
