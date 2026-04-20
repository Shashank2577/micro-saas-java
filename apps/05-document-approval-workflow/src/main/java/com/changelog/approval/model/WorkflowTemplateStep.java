package com.changelog.approval.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workflow_template_steps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTemplateStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private WorkflowTemplate template;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(nullable = false)
    private String name;

    @Column(name = "routing_mode", nullable = false)
    @Builder.Default
    private String routingMode = "sequential";

    @Column(name = "approver_ids", columnDefinition = "uuid[]")
    private List<UUID> approverIds;

    @Column(name = "approver_role")
    private String approverRole;

    @Column(name = "deadline_days")
    private Integer deadlineDays;

    @Column(name = "require_all", nullable = false)
    @Builder.Default
    private Boolean requireAll = true;
}
