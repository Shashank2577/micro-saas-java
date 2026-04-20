package com.changelog.service;

import com.changelog.model.OnboardingInstance;
import com.changelog.model.OnboardingTemplate;
import com.changelog.model.TaskInstance;
import com.changelog.model.TemplateTask;
import com.changelog.repository.OnboardingInstanceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingInstanceService {

    private final OnboardingInstanceRepository instanceRepository;
    private final OnboardingTemplateService templateService;

    @Transactional(readOnly = true)
    public List<OnboardingInstance> getActiveInstances(UUID tenantId) {
        return instanceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public OnboardingInstance getInstance(UUID instanceId, UUID tenantId) {
        return instanceRepository.findByIdAndTenantId(instanceId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Instance not found"));
    }

    @Transactional(readOnly = true)
    public OnboardingInstance getInstanceByToken(String portalToken) {
        return instanceRepository.findByPortalToken(portalToken)
                .orElseThrow(() -> new EntityNotFoundException("Instance not found for token"));
    }

    @Transactional
    public OnboardingInstance startOnboarding(UUID tenantId, UUID templateId, OnboardingInstance requestData, UUID createdBy) {
        OnboardingTemplate template = templateService.getTemplate(templateId, tenantId);

        OnboardingInstance instance = OnboardingInstance.builder()
                .tenantId(tenantId)
                .template(template)
                .hireName(requestData.getHireName())
                .hireEmail(requestData.getHireEmail())
                .hireRole(requestData.getHireRole())
                .department(requestData.getDepartment())
                .managerId(requestData.getManagerId())
                .buddyId(requestData.getBuddyId())
                .startDate(requestData.getStartDate())
                .status("active")
                .portalToken(UUID.randomUUID().toString())
                .createdBy(createdBy)
                .build();

        for (TemplateTask task : template.getTasks()) {
            TaskInstance taskInstance = TaskInstance.builder()
                    .onboardingInstance(instance)
                    .templateTask(task)
                    .title(task.getTitle())
                    .description(task.getDescription())
                    .taskType(task.getTaskType())
                    .dueDate(calculateDueDate(requestData.getStartDate(), task.getDueDayOffset()))
                    .status("pending")
                    .build();

            resolveAssignee(taskInstance, task, requestData);
            instance.getTasks().add(taskInstance);
        }

        return instanceRepository.save(instance);
    }

    @Transactional
    public void cancelOnboarding(UUID instanceId, UUID tenantId) {
        OnboardingInstance instance = getInstance(instanceId, tenantId);
        instance.setStatus("cancelled");
        instanceRepository.save(instance);
    }

    private LocalDate calculateDueDate(LocalDate startDate, int offsetDays) {
        return startDate.plusDays(offsetDays);
    }

    private void resolveAssignee(TaskInstance taskInstance, TemplateTask templateTask, OnboardingInstance instance) {
        switch (templateTask.getAssigneeType()) {
            case "new_hire":
                taskInstance.setAssigneeEmail(instance.getHireEmail());
                break;
            case "manager":
                taskInstance.setAssigneeId(instance.getManagerId());
                break;
            case "buddy":
                taskInstance.setAssigneeId(instance.getBuddyId());
                break;
            default:
                // hr, it, etc. could be resolved based on tenant config. Left generic here.
                break;
        }
    }
}
