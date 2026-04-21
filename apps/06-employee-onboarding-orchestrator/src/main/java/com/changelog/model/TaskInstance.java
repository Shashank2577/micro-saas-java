package com.changelog.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboarding_id", nullable = false)
    private OnboardingInstance onboardingInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_task_id")
    private TemplateTask templateTask;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "assignee_email")
    private String assigneeEmail;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private String status; // pending | completed | skipped

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by")
    private String completedBy;

    @OneToOne(mappedBy = "taskInstance", cascade = CascadeType.ALL)
    private TaskSubmission submission;
}
