# SPEC - WO-005: Fix App 06 AI Onboarding — Wire to Real LLM

## Problem Definition
The `AiOnboardingService` in the employee onboarding application (App 06) uses hardcoded stubs for generating onboarding plans and rewriting task descriptions. These need to be integrated with the real LLM gateway via `AiService` from the `saas-os-core` module.

## Proposed Changes

### saas-os-core
- **AiService.java**: Add a public method `callLlmRaw(String prompt)` that wraps the existing private `callLlm(String prompt)` method. This allows other modules to use the LLM for custom prompts and raw string responses.

### apps/06-employee-onboarding-orchestrator
- **AiOnboardingService.java**:
    - Inject `AiService`.
    - Update `generatePlan()` to use `AiService.callLlmRaw()` with a specific prompt to generate 8-12 tasks in JSON format.
    - Add `parseTasksFromJson()` to handle LLM response parsing, including markdown fence stripping and JSON mapping to `TemplateTask`.
    - Add `defaultTasks()` as a fallback when AI generation fails.
    - Update `rewriteDescriptions()` to use `AiService.callLlmRaw()` for each task description.
    - Ensure graceful degradation: use fallback or keep original data if LLM calls fail.
    - Remove the unused `createTask()` helper.
    - Add required imports (`ObjectMapper`, `JsonNode`, `ArrayList`, `List`, `AiService`).

## Acceptance Criteria
- `AiService.callLlmRaw` exists and is public.
- `generatePlan` in App 06 returns AI-generated tasks.
- `rewriteDescriptions` in App 06 updates descriptions using the LLM.
- Graceful degradation works for both methods.
- `createTask` is removed.
- Successful build of both modules.
