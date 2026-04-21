1. **Create `EntityNotFoundException.java`** in `saas-os-core/src/main/java/com/changelog/exception/`.
2. **Create `ForbiddenException.java`** in `saas-os-core/src/main/java/com/changelog/exception/`.
3. **Update `GlobalExceptionHandler.java`** in `saas-os-core` to map `ForbiddenException` to 403 and `IllegalStateException` to 409.
4. **Build `saas-os-core`**: `mvn install -pl saas-os-core`.
5. **Update App 02** (`BoardService`, `PostService`, `PublicFeedbackController` etc.) to use `EntityNotFoundException`.
6. **Update App 04** (`ClientService`, `InvoiceService`) to use `EntityNotFoundException`.
7. **Update App 05** (`DocumentService`, `WorkflowService`, `ApprovalService`, `WorkflowTemplateService`) to use `EntityNotFoundException`, `ForbiddenException`, and `IllegalStateException` where required.
8. **Update App 06** (`OnboardingTemplateService`, `TaskInstanceService`, `AiOnboardingService`, `OnboardingInstanceService`) to use `EntityNotFoundException`.
9. **Update App 07** (`ProjectService`, `IssueService`, `LabelService`, `CommentService` if any) to use `EntityNotFoundException`.
10. **Update App 10** (`ObjectiveService`, `OkrCycleService`) to use `EntityNotFoundException`. Add correct tenant ownership check in `ObjectiveService.updateKeyResult()`.
