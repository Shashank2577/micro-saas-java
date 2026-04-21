package com.changelog.apikey.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portal_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalConfig {
    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "primary_color", nullable = false)
    private String primaryColor;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "app_name", nullable = false)
    private String appName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_origins", columnDefinition = "text[]")
    private List<String> allowedOrigins;

    @Column(name = "available_scopes_enabled", nullable = false)
    private boolean availableScopesEnabled;
}
