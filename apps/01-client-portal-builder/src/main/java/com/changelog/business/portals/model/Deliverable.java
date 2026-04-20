package com.changelog.business.portals.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "deliverables")
@Getter
@Setter
public class Deliverable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;

    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval = false;
}
