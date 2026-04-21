# WO-005: Fix App 06 AI Onboarding — Wire to Real LLM

## Problem
`AiOnboardingService` in `apps/06-employee-onboarding-orchestrator` has two fully stubbed methods:

1. `generatePlan()` — returns 3 hardcoded tasks ("Welcome to the Company", "IT Setup", "Manager 1:1") regardless of job title or department.
2. `rewriteDescriptions()` — prepends "AI Rewritten: " to each task description without calling the LLM.

Both methods need to be wired to the real LiteLLM gateway via `AiService` in `saas-os-core`.

## What to Do

### 1. Add `callLlmRaw()` to AiService in saas-os-core

File: `saas-os-core/src/main/java/com/changelog/ai/AiService.java`

The private `callLlm(String prompt)` method must be made accessible to other Spring beans. Add this public method that delegates to the existing private one:

```java
/**
 * Exposes the private LLM call for services in other modules.
 * Returns the raw string from the model. Throws RuntimeException on failure.
 */
public String callLlmRaw(String prompt) {
    return callLlm(prompt);
}
```

Do NOT rename or modify the existing private `callLlm()` method — just add the new public wrapper. Rebuild saas-os-core after this change: `mvn install -pl saas-os-core`.

### 2. Add `AiService` dependency to `AiOnboardingService`

File: `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/service/AiOnboardingService.java`

Remove the current import and field `OnboardingTemplateRepository templateRepository` — it is still needed. Add `AiService` from saas-os-core as an additional constructor-injected dependency:

```java
import com.changelog.ai.AiService;
// ...
private final OnboardingTemplateRepository templateRepository;
private final AiService aiService;  // from saas-os-core
```

The class already uses `@RequiredArgsConstructor` — simply add the field and Lombok will generate the constructor parameter.

### 3. Implement Real `generatePlan()`

Replace the body of `generatePlan()` with:

```java
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
```

Add private helper `parseTasksFromJson()`:

```java
private List<TemplateTask> parseTasksFromJson(String json, OnboardingTemplate template) {
    // Strip markdown fences if present
    String cleaned = json.trim();
    if (cleaned.startsWith("```")) {
        int nl = cleaned.indexOf('\n');
        if (nl > 0) cleaned = cleaned.substring(nl + 1);
    }
    if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3).trim();

    try {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode arr = mapper.readTree(cleaned);
        List<TemplateTask> result = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode node : arr) {
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
```

Add private fallback `defaultTasks()`:

```java
private List<TemplateTask> defaultTasks(OnboardingTemplate template) {
    List<TemplateTask> tasks = new java.util.ArrayList<>();
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
```

### 4. Implement Real `rewriteDescriptions()`

Replace the body of `rewriteDescriptions()` with:

```java
@Transactional
public OnboardingTemplate rewriteDescriptions(UUID templateId, UUID tenantId) {
    log.info("Rewriting descriptions using AI for template {}", templateId);

    OnboardingTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
            .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));

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
```

### 5. Remove `createTask()` Helper

The existing private `createTask()` method in `AiOnboardingService` is no longer used after this refactor. Remove it to keep the class clean.

### 6. Graceful Degradation
- If `aiService.callLlmRaw()` throws (gateway down), `generatePlan()` catches the exception, logs a warning, and falls back to `defaultTasks()` — always returns a valid template.
- If `callLlmRaw()` throws inside `rewriteDescriptions()` for a specific task, that task keeps its original description and processing continues for remaining tasks. Do NOT throw 500 to the client.

## Required Imports for AiOnboardingService

```java
import com.changelog.ai.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
```

## Acceptance Criteria
1. `AiService.callLlmRaw(String prompt)` public method exists in saas-os-core
2. `POST /api/v1/ai/generate-plan?jobTitle=...&department=...` returns a template with 8+ AI-generated tasks when LiteLLM gateway is reachable
3. `POST /api/v1/ai/rewrite-descriptions/{templateId}` updates task descriptions with real LLM output
4. When gateway is unreachable: `generatePlan()` returns a template with 3 default tasks (not 500)
5. When gateway is unreachable: `rewriteDescriptions()` returns the unchanged template (not 500)
6. The `createTask()` private helper method is removed
7. `mvn compile -pl saas-os-core,apps/06-employee-onboarding-orchestrator` succeeds
8. `mvn install -pl saas-os-core` succeeds (required before building App 06)

## Tech Stack
- Java 21, Spring Boot 3.3.5
- `AiService` is at `saas-os-core/src/main/java/com/changelog/ai/AiService.java`
- `@RequiredArgsConstructor` (Lombok) — adding a field auto-adds constructor parameter
- `com.fasterxml.jackson.databind.ObjectMapper` — already a transitive dependency via Spring Boot
- LiteLLM gateway: `${ai.gateway-url:http://localhost:4000}` — OpenAI-compatible
- Do NOT add Spring AI or LangChain4j; call LLM through `AiService` only
