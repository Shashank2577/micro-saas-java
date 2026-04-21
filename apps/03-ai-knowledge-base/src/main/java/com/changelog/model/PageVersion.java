package com.changelog.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "page_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private KbPage page;

    @Column(name = "version_num", nullable = false)
    private Integer versionNum;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private String title;

    @Column(name = "edited_by")
    private UUID editedBy;

    @CreationTimestamp
    @Column(name = "edited_at", nullable = false, updatable = false)
    private OffsetDateTime editedAt;
}