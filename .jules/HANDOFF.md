# Employee Onboarding Orchestrator (WO-06)

## Implementation Details
This PR implements App 06: Employee Onboarding Orchestrator.
The app is located in `apps/06-employee-onboarding-orchestrator` and provides a template-driven onboarding orchestration platform.

### Structure
1. **Entities & Repositories**:
   - `OnboardingTemplate`, `TemplateTask`, `OnboardingInstance`, `TaskInstance`, `TaskSubmission`.
2. **Services**:
   - `OnboardingTemplateService`: Manages templates and their tasks.
   - `OnboardingInstanceService`: Handles starting an onboarding, auto-calculating due dates, generating a magic token, and assigning tasks.
   - `TaskInstanceService`: Logic for completing, skipping, or submitting tasks, and automatically closing instances when all tasks are done.
   - `AiOnboardingService`: A stubbed AI generator that provides onboarding templates using mock data for B2B testing.
3. **Controllers**:
   - Exposed all endpoints mentioned in the spec across `OnboardingTemplateController`, `OnboardingInstanceController`, `TaskController`, `PortalController`, and `AiOnboardingController`.
   - Used `X-Tenant-ID` header simulation to enforce strict multi-tenancy access (as standard Keycloak context propagation relies on security configuration omitted in the sandbox).
4. **Flyway Migrations**:
   - Created `V1__onboarding.sql` to setup the database correctly.
   - Set `ddl-auto: validate` to safely manage schema without overwrite risks.

### Spec Deviations
- **AI Integration**: The `AiOnboardingService` uses mocked AI logic. Since the core AI gateway depends on API keys and external infrastructure not guaranteed to run correctly without a network, it generates structured template mocks simulating AI output.
- **Tenant Context**: Simulated using an `X-Tenant-ID` header since the full Keycloak filter configuration is out of scope for the backend-only sandbox.

### How to Verify
1. Compile the app:
   ```bash
   mvn clean compile -pl apps/06-employee-onboarding-orchestrator -am
   ```
2. The code builds successfully, passing validation checks.
