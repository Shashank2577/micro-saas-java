package com.changelog.business.portals.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "portal_messages")
@Getter
@Setter
public class PortalMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portal_id", nullable = false)
    private UUID portalId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_role", nullable = false)
    private String authorRole;

    @Column(nullable = false)
    private String body;

    @Column(name = "parent_id")
    private UUID parentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
