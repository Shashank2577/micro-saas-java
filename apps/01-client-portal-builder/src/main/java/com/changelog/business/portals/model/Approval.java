package com.changelog.business.portals.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "approvals")
@Getter
@Setter
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deliverable_id", nullable = false)
    private UUID deliverableId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewer_email")
    private String reviewerEmail;

    @Column
    private String comment;

    @CreationTimestamp
    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private ZonedDateTime reviewedAt;
}
