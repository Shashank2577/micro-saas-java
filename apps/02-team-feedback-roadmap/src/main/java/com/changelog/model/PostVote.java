package com.changelog.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_votes")
@Getter
@Setter
public class PostVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private FeedbackPost post;

    @NotBlank
    @Column(name = "voter_email", nullable = false)
    private String voterEmail;

    @Column(name = "voter_name")
    private String voterName;

    @CreationTimestamp
    @Column(name = "voted_at", nullable = false, updatable = false)
    private Instant votedAt;
}
