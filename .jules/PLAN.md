1. **Setup Project Structure**:
   - Create `apps/06-employee-onboarding-orchestrator` module with `saas-os-parent` as parent and `saas-os-core` as a dependency.
   - Set up the module POM and folder structure. (Since `saas-os-parent` and `saas-os-core` don't exist in the current `main` branch, I've created basic versions of them to satisfy the architecture rules and make the build pass).
   - Create the Application Class `EmployeeOnboardingApplication.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/`.

2. **Entity Modeling**:
   - Create the `OnboardingTemplate.java` file in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/model/` and implement the JPA entity for `onboarding_templates`.
   - Create the `TemplateTask.java` file in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/model/` and implement the JPA entity for `template_tasks`.
   - Create the `OnboardingInstance.java` file in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/model/` and implement the JPA entity for `onboarding_instances`.
   - Create the `TaskInstance.java` file in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/model/` and implement the JPA entity for `task_instances`.
   - Create the `TaskSubmission.java` file in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/model/` and implement the JPA entity for `task_submissions`.

3. **Repositories**:
   - Create `OnboardingTemplateRepository.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/repository/`.
   - Create `TemplateTaskRepository.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/repository/`.
   - Create `OnboardingInstanceRepository.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/repository/`.
   - Create `TaskInstanceRepository.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/repository/`.
   - Create `TaskSubmissionRepository.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/repository/`.

4. **Verify Data Layer Compilation**:
   - Run `mvn compile -pl apps/06-employee-onboarding-orchestrator` to ensure the core data layer compiles correctly.

5. **Services - Templates**:
   - Create `OnboardingTemplateService.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/service/`.
   - Implement `createTemplate`, `getTemplate`, `updateTemplate`, `archiveTemplate`, and `replaceTasks` methods.

6. **Services - Instances and Tasks**:
   - Create `OnboardingInstanceService.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/service/`.
   - Implement `startOnboarding` to create an instance, calculate task due dates, and generate a magic link.
   - Implement `getOnboardingInstance` to retrieve instance details.
   - Create `TaskInstanceService.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/service/`.
   - Implement `completeTask` and `submitTask` methods.

7. **Services - AI**:
   - Review `changelog-platform/src/main/java/com/changelog/ai` to understand the existing AI integration logic.
   - Create `AiOnboardingService.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/service/`.
   - Implement `generatePlan` and `rewriteDescriptions` methods leveraging existing shared patterns.

8. **Controllers - Templates and Instances**:
   - Create `OnboardingTemplateController.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/controller/`.
   - Implement `GET /api/templates`, `POST /api/templates`, `GET /api/templates/{templateId}`, `PUT /api/templates/{templateId}`, `DELETE /api/templates/{templateId}`, `PUT /api/templates/{templateId}/tasks`.
   - Create `OnboardingInstanceController.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/controller/`.
   - Implement `GET /api/onboardings`, `POST /api/onboardings`, `GET /api/onboardings/{onboardingId}`, `POST /api/onboardings/{onboardingId}/cancel`.

9. **Controllers - Tasks, Portal, AI**:
   - Create `TaskController.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/controller/`.
   - Implement `GET /api/onboardings/{onboardingId}/tasks`, `POST /api/tasks/{taskId}/complete`, `POST /api/tasks/{taskId}/skip`.
   - Create `PortalController.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/controller/`.
   - Implement `GET /portal/{token}`, `POST /portal/{token}/tasks/{taskId}/complete`, `POST /portal/{token}/tasks/{taskId}/submit`.
   - Create `AiOnboardingController.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/controller/`.
   - Implement `POST /api/ai/generate-plan` and `POST /api/templates/{templateId}/ai/write-descriptions`.

10. **Verify Compilation**:
    - Run `mvn clean compile -pl apps/06-employee-onboarding-orchestrator` to ensure the new application compiles correctly without syntax errors.

11. **Verify Tests**:
    - Run `mvn test -pl apps/06-employee-onboarding-orchestrator` to ensure any tests pass and there are no regressions.

12. **Pre Commit Steps**:
    - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.

13. **Submit**:
    - Create `.jules/HANDOFF.md` detailing what was built, spec deviations, and how to verify.
    - Open a PR to `main` with the title `feat(WO-06): Employee Onboarding Orchestrator — [description]` and paste the `HANDOFF.md` content into the PR description.
