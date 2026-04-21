package com.changelog.approval.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_step_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowInstance workflow;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "assignee_email")
    private String assigneeEmail;

    @Column
    private String action;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
