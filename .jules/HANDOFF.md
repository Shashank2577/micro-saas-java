# HANDOFF - WO-005: Fix App 06 AI Onboarding — Wire to Real LLM

## Summary
The AI onboarding functionality in App 06 has been wired to the real LiteLLM gateway. Stubs have been replaced with actual LLM calls while maintaining robust fallback mechanisms.

## Key Changes
- **saas-os-core**: Added public `callLlmRaw` method to `AiService` to allow raw string interaction with the LLM.
- **App 06 (Onboarding)**:
    - Integrated `AiService` into `AiOnboardingService`.
    - `generatePlan` now uses the LLM to generate 8-12 tasks based on job title and department.
    - `rewriteDescriptions` now uses the LLM to polish each task's description.
    - Implemented JSON parsing for LLM responses with markdown fence stripping.
    - Added fallback logic: `defaultTasks` for plan generation and keeping original description for rewriting on failure.
    - Removed unused `createTask` helper.
- **Testing**: Added `AiOnboardingServiceTest` with 100% coverage of the new logic, including error scenarios.

## Verification
- Run `mvn install -pl saas-os-core` then `mvn test -pl apps/06-employee-onboarding-orchestrator` to verify.
