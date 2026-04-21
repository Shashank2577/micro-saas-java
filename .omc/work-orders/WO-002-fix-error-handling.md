# WO-002: Fix Error Handling — Domain Exceptions Instead of RuntimeException

## Problem
The shared `GlobalExceptionHandler` in saas-os-core maps `EntityNotFoundException` → 404 and `IllegalArgumentException` → 400. But almost every service in apps 02, 04, 05, 06, 07, 10 throws bare `RuntimeException` in `orElseThrow()` calls. This means "entity not found" returns HTTP 500 instead of 404, and "access denied" returns HTTP 500 instead of 403.

## GlobalExceptionHandler location
`saas-os-core/src/main/java/com/changelog/config/GlobalExceptionHandler.java`

Current handlers:
- `EntityNotFoundException` → 404
- `IllegalArgumentException` → 400
- `MethodArgumentNotValidException` → 400 with field errors
- `Exception` (catch-all) → 500

## What's Missing
1. A `ForbiddenException` or use of `AccessDeniedException` mapped to 403
2. Correct usage of `EntityNotFoundException` in all service `orElseThrow()` calls

## Existing Exception Classes
Check whether `com.changelog.exception.EntityNotFoundException` already exists in saas-os-core. If it does, use it. If not, create it at `saas-os-core/src/main/java/com/changelog/exception/EntityNotFoundException.java` as a simple unchecked exception extending `RuntimeException` with a String message constructor.

Also create `saas-os-core/src/main/java/com/changelog/exception/ForbiddenException.java` extending `RuntimeException`.

Add handlers to `GlobalExceptionHandler`:
```java
@ExceptionHandler(ForbiddenException.class)
public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
}
```

`ErrorResponse` is at `saas-os-core/src/main/java/com/changelog/dto/ErrorResponse.java` — use that class.

## Files to Fix

### App 02 — Team Feedback & Roadmap
Files: all `*Service.java` in `apps/02-team-feedback-roadmap/src/main/java/com/changelog/service/`

Pattern to apply: Replace every `.orElseThrow(() -> new RuntimeException("X not found"))` with:
```java
.orElseThrow(() -> new EntityNotFoundException("Board not found: " + id))
```
Import: `com.changelog.exception.EntityNotFoundException`

Specific instances to find:
- `BoardService` — `getBoard(UUID boardId, UUID tenantId)` method
- `FeedbackPostService` — `getPost(...)` method, `getBoard(...)` method
- Any other `orElseThrow` call

### App 04 — Invoice Payment Tracker
Files: all `*Service.java` in `apps/04-invoice-payment-tracker/src/main/java/com/changelog/invoice/service/`

Apply same pattern to:
- `ClientService.getClient()` — currently `orElseThrow(() -> new RuntimeException(...))`
- `InvoiceService.getInvoice()` — same
- `InvoiceService.getInvoiceByPublicToken()` — same

### App 05 — Document Approval Workflow
Files: `apps/05-document-approval-workflow/src/main/java/com/changelog/approval/service/`

- `DocumentService` — all `orElseThrow()` calls
- `DocumentService` line ~41 — `throw new RuntimeException("Access denied")` → `throw new ForbiddenException("You do not have access to this document")`
- `WorkflowService` — all `orElseThrow()` calls
- `WorkflowService` line ~45 — access denied throws → `ForbiddenException`
- `WorkflowService` — `throw new RuntimeException("Workflow is not active")` → `throw new IllegalStateException(...)` which should be caught by a new handler returning 409 Conflict. Add `IllegalStateException` → 409 handler to `GlobalExceptionHandler`.

### App 06 — Employee Onboarding Orchestrator
Files: all `*Service.java` in `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/service/`

- `OnboardingTemplateService` — all `orElseThrow()` calls
- `OnboardingInstanceService` — all `orElseThrow()` calls

### App 07 — Lightweight Issue Tracker
Files: all `*Service.java` in `apps/07-lightweight-issue-tracker/src/main/java/com/changelog/issuetracker/service/`

- `IssueService` — all `orElseThrow()` calls where the entity isn't found by tenantId combo
- `ProjectService` — same
- `CommentService` — same

### App 10 — OKR Goal Tracker
Files: all `*Service.java` in `apps/10-okr-goal-tracker/src/main/java/com/changelog/okr/service/`

- `OkrCycleService` — all `orElseThrow()` calls
- `ObjectiveService` — all `orElseThrow()` calls
- `ObjectiveService.updateKeyResult()` — when tenant doesn't own the KR → `EntityNotFoundException` (not ForbiddenException — don't leak existence)

## Updated GlobalExceptionHandler
Add to `saas-os-core/src/main/java/com/changelog/config/GlobalExceptionHandler.java`:
```java
@ExceptionHandler(ForbiddenException.class)
public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
}

@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("CONFLICT", ex.getMessage()));
}
```

## Acceptance Criteria
1. `EntityNotFoundException` class exists in `com.changelog.exception` package in saas-os-core
2. `ForbiddenException` class exists in `com.changelog.exception` package in saas-os-core
3. `GlobalExceptionHandler` maps `ForbiddenException` → 403, `IllegalStateException` → 409
4. Every `orElseThrow(() -> new RuntimeException(...not found...))` in the listed apps is replaced with `EntityNotFoundException`
5. Every "access denied" throw in listed apps uses `ForbiddenException`
6. `mvn compile -pl saas-os-core,apps/02-team-feedback-roadmap,apps/04-invoice-payment-tracker,apps/05-document-approval-workflow,apps/06-employee-onboarding-orchestrator,apps/07-lightweight-issue-tracker,apps/10-okr-goal-tracker` completes without errors
7. Rebuild saas-os-core after adding exceptions: `mvn install -pl saas-os-core`

## Tech Stack
- Java 21, Spring Boot 3.3.5
- Exception hierarchy: all custom exceptions extend `RuntimeException` (unchecked)
- `@ControllerAdvice` on `GlobalExceptionHandler` already exists — only add new `@ExceptionHandler` methods
- Do NOT change the existing handlers for `EntityNotFoundException`, `IllegalArgumentException`, `MethodArgumentNotValidException`, or the catch-all `Exception`
