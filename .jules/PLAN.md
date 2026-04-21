# PLAN - WO-005: Fix App 06 AI Onboarding — Wire to Real LLM

## Steps

### 1. Modify `saas-os-core`
- Edit `saas-os-core/src/main/java/com/changelog/ai/AiService.java` to add `callLlmRaw(String prompt)`.
- Rebuild `saas-os-core` using `mvn install -pl saas-os-core`.

### 2. Modify `apps/06-employee-onboarding-orchestrator`
- Edit `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/service/AiOnboardingService.java`.
    - Add `AiService` field and update imports.
    - Implement `generatePlan()` with real LLM call and fallback logic.
    - Implement `parseTasksFromJson()` helper.
    - Implement `defaultTasks()` fallback helper.
    - Implement `rewriteDescriptions()` with real LLM call.
    - Remove `createTask()` helper.
- Verify compilation of App 06 using `mvn compile -pl apps/06-employee-onboarding-orchestrator`.

### 3. Verification
- Run tests for App 06 if available.
- Check build of both modules.

### 4. Documentation
- Create `IMPLEMENTATION_LOG.md`.
- Create `VERIFICATION_REPORT.md`.
- Create `HANDOFF.md`.
