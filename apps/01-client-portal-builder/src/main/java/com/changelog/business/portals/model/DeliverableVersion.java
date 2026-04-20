package com.changelog.business.portals.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "deliverable_versions")
@Getter
@Setter
public class DeliverableVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deliverable_id", nullable = false)
    private UUID deliverableId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private ZonedDateTime uploadedAt;
}
