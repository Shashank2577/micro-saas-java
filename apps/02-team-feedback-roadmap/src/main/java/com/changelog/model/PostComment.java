package com.changelog.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_comments")
@Getter
@Setter
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private FeedbackPost post;

    @NotBlank
    @Column(name = "author_email", nullable = false)
    private String authorEmail;

    @Column(name = "author_name")
    private String authorName;

    @NotBlank
    @Column(name = "author_role", nullable = false)
    private String authorRole = "customer"; // customer | team

    @NotBlank
    @Column(nullable = false)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
