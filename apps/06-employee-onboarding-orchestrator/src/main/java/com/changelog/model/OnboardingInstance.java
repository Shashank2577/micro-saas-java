package com.changelog.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "onboarding_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private OnboardingTemplate template;

    @Column(name = "hire_name", nullable = false)
    private String hireName;

    @Column(name = "hire_email", nullable = false)
    private String hireEmail;

    @Column(name = "hire_role")
    private String hireRole;

    @Column(name = "department")
    private String department;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "buddy_id")
    private UUID buddyId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private String status; // active | completed | cancelled

    @Column(name = "portal_token", nullable = false, unique = true)
    private String portalToken;

    @Column(name = "portal_opened_at")
    private LocalDateTime portalOpenedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "onboardingInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TaskInstance> tasks = new ArrayList<>();
}
