package com.changelog.service;

import com.changelog.model.OnboardingTemplate;
import com.changelog.model.TemplateTask;
import com.changelog.repository.OnboardingTemplateRepository;
import com.changelog.repository.TemplateTaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingTemplateService {

    private final OnboardingTemplateRepository templateRepository;
    private final TemplateTaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<OnboardingTemplate> getActiveTemplates(UUID tenantId) {
        return templateRepository.findByTenantIdAndIsActiveTrue(tenantId);
    }

    @Transactional(readOnly = true)
    public OnboardingTemplate getTemplate(UUID templateId, UUID tenantId) {
        return templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found"));
    }

    @Transactional
    public OnboardingTemplate createTemplate(OnboardingTemplate template) {
        if (template.getTasks() != null) {
            for (TemplateTask task : template.getTasks()) {
                task.setTemplate(template);
            }
        }
        return templateRepository.save(template);
    }

    @Transactional
    public OnboardingTemplate updateTemplate(UUID templateId, UUID tenantId, OnboardingTemplate updatedTemplate) {
        OnboardingTemplate existing = getTemplate(templateId, tenantId);
        existing.setName(updatedTemplate.getName());
        existing.setDescription(updatedTemplate.getDescription());
        existing.setCategory(updatedTemplate.getCategory());
        return templateRepository.save(existing);
    }

    @Transactional
    public void archiveTemplate(UUID templateId, UUID tenantId) {
        OnboardingTemplate template = getTemplate(templateId, tenantId);
        template.setActive(false);
        templateRepository.save(template);
    }

    @Transactional
    public OnboardingTemplate replaceTasks(UUID templateId, UUID tenantId, List<TemplateTask> newTasks) {
        OnboardingTemplate template = getTemplate(templateId, tenantId);

        // Clear existing tasks
        template.getTasks().clear();

        // Add new tasks
        for (int i = 0; i < newTasks.size(); i++) {
            TemplateTask task = newTasks.get(i);
            task.setTemplate(template);
            task.setPosition(i);
            template.getTasks().add(task);
        }

        return templateRepository.save(template);
    }
}
