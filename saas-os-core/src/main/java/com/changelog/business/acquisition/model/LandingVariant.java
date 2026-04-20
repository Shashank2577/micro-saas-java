package com.changelog.business.acquisition.model;

import com.changelog.business.acquisition.dto.CreateLandingPageRequest.LandingVariantRequest.Feature;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "landing_variants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "page_id", nullable = false)
    private UUID pageId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_control")
    @Builder.Default
    private Boolean isControl = false;

    @Column(name = "traffic_split")
    @Builder.Default
    private Integer trafficSplit = 50;

    @Column(nullable = false)
    @Builder.Default
    private String status = "active";

    @Column(nullable = false)
    private String headline;

    @Column
    private String subheadline;

    @Column(name = "cta_text", nullable = false)
    private String ctaText;

    @Column(name = "cta_link")
    private String ctaLink;

    @Column(columnDefinition = "TEXT")
    private String bodyContent;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "hero_image_url")
    private String heroImageUrl;

    @Column
    private String testimonial;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private List<Feature> features;

    @Column(name = "meta_title")
    private String metaTitle;

    @Column(name = "meta_description")
    private String metaDescription;

    @Column(name = "og_image_url")
    private String ogImageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Integer visitors = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer conversions = 0;

    @Column(name = "conversion_rate", columnDefinition = "NUMERIC")
    private Double conversionRate;
}
