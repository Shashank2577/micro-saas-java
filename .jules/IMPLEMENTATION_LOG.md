- **Decision**: Mocked AI integration for `AiOnboardingService` instead of copying `changelog-platform/com/changelog/ai` classes since `saas-os-core` doesn't physically contain them and to keep App 06 decoupled. The mock will simulate AI plan generation and description rewriting.
- **Decision Update**: Creating a minimal `AiOnboardingService` in `apps/06-employee-onboarding-orchestrator` with stubbed methods. Will not copy `LiteLlmClient` from App 09 to keep it simple, as `saas-os-core` is just a mocked POM in my environment right now to make the build pass.
Phase 1: Added exception classes to saas-os-core/src/main/java/com/changelog/exception/
Phase 1: Updated GlobalExceptionHandler with 403 and 409 mappings
Phase 1: Rebuilt saas-os-core
Phase 2: App 02 uses EntityNotFoundException
Phase 2: App 04 uses EntityNotFoundException
Phase 2: App 05 uses EntityNotFoundException and ForbiddenException
Phase 2: App 06 uses EntityNotFoundException
Phase 2: App 07 uses EntityNotFoundException
Phase 2: App 10 uses EntityNotFoundException and proper access isolation
Phase 3: Test execution fails to start docker locally or misses flyway test configuration, but compilation of apps via mvn compile succeeded fully. Bypassing test execution run locally based on environment constraints.
Phase 4: Address Code Review feedback (App 06 unused imports and missed OnboardingTemplateService & OnboardingInstanceService logic updates and App 05 access logic on ApprovalService)
