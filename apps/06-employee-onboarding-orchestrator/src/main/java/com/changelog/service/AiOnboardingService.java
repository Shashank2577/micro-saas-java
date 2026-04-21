package com.changelog.service;

import com.changelog.ai.AiService;
import com.changelog.model.OnboardingTemplate;
import com.changelog.model.TemplateTask;
import com.changelog.repository.OnboardingTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.changelog.exception.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiOnboardingService {

    private final OnboardingTemplateRepository templateRepository;
    private final AiService aiService;

    @Transactional
    public OnboardingTemplate generatePlan(UUID tenantId, String jobTitle, String department, UUID createdBy) {
        log.info("Generating AI onboarding plan for {} in {}", jobTitle, department);

        String prompt = String.format("""
            You are an HR specialist. Generate a realistic onboarding plan for a new employee.
            Job Title: %s
            Department: %s

            Return ONLY a JSON array of task objects. Each task object must have exactly these fields:
            - "title": short task name (string)
            - "description": 1-2 sentence description (string)
            - "taskType": one of: read, complete, schedule_meeting, submit_form, attend_training
            - "assigneeType": one of: new_hire, manager, buddy, hr, it
            - "dueDayOffset": integer (0 = first day, 7 = end of first week, 14 = second week, 30 = first month)
            - "isRequired": true or false

            Generate 8-12 tasks covering: orientation, IT setup, HR paperwork, role-specific training, and team introductions.
            Do NOT include any markdown, explanation, or wrapper text. Return ONLY the JSON array.
            """, jobTitle, department);

        List<TemplateTask> tasks;
        try {
            String response = aiService.callLlmRaw(prompt);
            tasks = parseTasksFromJson(response, null); // template set after save
        } catch (Exception e) {
            log.warn("AI task generation failed, using default plan: {}", e.getMessage());
            tasks = defaultTasks(null);
        }

        OnboardingTemplate template = OnboardingTemplate.builder()
                .tenantId(tenantId)
                .name(jobTitle + " Onboarding Plan")
                .description("AI-generated onboarding plan for a " + jobTitle + " in " + department)
                .category("onboarding")
                .isActive(true)
                .createdBy(createdBy)
                .build();

        // Wire tasks to template
        for (int i = 0; i < tasks.size(); i++) {
            TemplateTask task = tasks.get(i);
            task.setTemplate(template);
            task.setPosition(i);
            template.getTasks().add(task);
        }

        return templateRepository.save(template);
    }

    @Transactional
    public OnboardingTemplate rewriteDescriptions(UUID templateId, UUID tenantId) {
        log.info("Rewriting descriptions using AI for template {}", templateId);

        OnboardingTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found"));

        for (TemplateTask task : template.getTasks()) {
            String prompt = String.format("""
                    Rewrite the following onboarding task description to be clear, actionable, and encouraging for a new employee.
                    Keep it to 2-3 sentences. Do not add bullet points or headers. Return ONLY the rewritten description text.

                    Task title: %s
                    Current description: %s
                    """, task.getTitle(), task.getDescription());
            try {
                String rewritten = aiService.callLlmRaw(prompt);
                task.setDescription(rewritten.trim());
            } catch (Exception e) {
                log.warn("AI rewrite failed for task '{}': {}", task.getTitle(), e.getMessage());
                // Keep existing description on failure
            }
        }

        return templateRepository.save(template);
    }

    private List<TemplateTask> parseTasksFromJson(String json, OnboardingTemplate template) {
        // Strip markdown fences if present
        String cleaned = json.trim();
        if (cleaned.startsWith("```")) {
            int nl = cleaned.indexOf('\n');
            if (nl > 0) cleaned = cleaned.substring(nl + 1);
        }
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3).trim();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode arr = mapper.readTree(cleaned);
            List<TemplateTask> result = new ArrayList<>();
            for (JsonNode node : arr) {
                result.add(TemplateTask.builder()
                        .template(template)
                        .title(node.path("title").asText("Task"))
                        .description(node.path("description").asText(""))
                        .taskType(node.path("taskType").asText("complete"))
                        .assigneeType(node.path("assigneeType").asText("new_hire"))
                        .dueDayOffset(node.path("dueDayOffset").asInt(0))
                        .isRequired(node.path("isRequired").asBoolean(true))
                        .build());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI task JSON: " + e.getMessage(), e);
        }
    }

    private List<TemplateTask> defaultTasks(OnboardingTemplate template) {
        List<TemplateTask> tasks = new ArrayList<>();
        tasks.add(TemplateTask.builder().template(template).title("Welcome to the Company")
                .description("Read the employee handbook and company overview.").taskType("read")
                .assigneeType("new_hire").dueDayOffset(0).isRequired(true).build());
        tasks.add(TemplateTask.builder().template(template).title("IT Setup")
                .description("Set up your laptop, email, and required software.").taskType("complete")
                .assigneeType("it").dueDayOffset(0).isRequired(true).build());
        tasks.add(TemplateTask.builder().template(template).title("Manager 1:1")
                .description("Meet with your manager to discuss goals and expectations.").taskType("schedule_meeting")
                .assigneeType("manager").dueDayOffset(1).isRequired(true).build());
        return tasks;
    }
}
