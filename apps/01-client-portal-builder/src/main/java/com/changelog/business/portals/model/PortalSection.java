package com.changelog.business.portals.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "portal_sections")
@Getter
@Setter
public class PortalSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portal_id", nullable = false)
    private UUID portalId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer position = 0;
}
