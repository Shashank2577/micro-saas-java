package com.changelog.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "widget_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WidgetConfig {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false)
    @Builder.Default
    private String position = "bottom-right";

    @Column(name = "trigger_type", nullable = false)
    @Builder.Default
    private String triggerType = "badge";

    @Column(name = "badge_label", nullable = false)
    @Builder.Default
    private String badgeLabel = "What's New";

    @Column(name = "primary_color", nullable = false)
    @Builder.Default
    private String primaryColor = "#4F46E5";

    @Column(name = "allowed_origins")
    @Builder.Default
    private String[] allowedOrigins = {};
}
