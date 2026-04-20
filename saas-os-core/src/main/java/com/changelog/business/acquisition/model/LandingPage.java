package com.changelog.business.acquisition.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "landing_pages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LandingPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    @Builder.Default
    private String status = "draft"; // draft | active | paused | archived

    @Column(name = "primary_domain")
    private String primaryDomain;

    @Column(name = "platform_hosted")
    private String platformHosted;

    @OneToMany(mappedBy = "pageId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LandingVariant> variants = new java.util.ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Transient
    public boolean isActive() {
        return "active".equals(status);
    }

    @Transient
    public String getUrl() {
        if (primaryDomain != null) {
            return "https://" + primaryDomain;
        }
        return platformHosted;
    }
}
