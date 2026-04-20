package com.changelog.dto;

import com.changelog.model.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private UUID id;
    private UUID tenantId;
    private String name;
    private String slug;
    private String description;
    private Map<String, Object> branding;
    private String customDomain;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse fromEntity(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .tenantId(project.getTenantId())
                .name(project.getName())
                .slug(project.getSlug())
                .description(project.getDescription())
                .branding(project.getBranding())
                .customDomain(project.getCustomDomain())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
