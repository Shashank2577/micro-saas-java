package com.changelog.service;

import com.changelog.model.OnboardingTemplate;
import com.changelog.model.TemplateTask;
import com.changelog.repository.OnboardingTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiOnboardingService {

    private final OnboardingTemplateRepository templateRepository;

    @Transactional
    public OnboardingTemplate generatePlan(UUID tenantId, String jobTitle, String department, UUID createdBy) {
        log.info("Generating AI onboarding plan for {} in {}", jobTitle, department);

        OnboardingTemplate template = OnboardingTemplate.builder()
                .tenantId(tenantId)
                .name(jobTitle + " Onboarding")
                .description("AI-generated onboarding plan for a " + jobTitle + " in the " + department + " department.")
                .category("onboarding")
                .isActive(true)
                .createdBy(createdBy)
                .build();

        // Stub AI tasks
        template.getTasks().add(createTask(template, "Welcome to the Company", "Read our handbook.", "read", "new_hire", 1, 0));
        template.getTasks().add(createTask(template, "IT Setup", "Set up laptop.", "complete", "new_hire", 1, 1));
        template.getTasks().add(createTask(template, "Manager 1:1", "Meet with manager.", "schedule_meeting", "manager", 2, 2));

        return templateRepository.save(template);
    }

    @Transactional
    public OnboardingTemplate rewriteDescriptions(UUID templateId, UUID tenantId) {
        log.info("Rewriting descriptions using AI for template {}", templateId);

        OnboardingTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        for (TemplateTask task : template.getTasks()) {
            task.setDescription("AI Rewritten: " + task.getDescription());
        }

        return templateRepository.save(template);
    }

    private TemplateTask createTask(OnboardingTemplate template, String title, String desc, String type, String assignee, int offset, int pos) {
        return TemplateTask.builder()
                .template(template)
                .title(title)
                .description(desc)
                .taskType(type)
                .assigneeType(assignee)
                .dueDayOffset(offset)
                .isRequired(true)
                .position(pos)
                .build();
    }
}
