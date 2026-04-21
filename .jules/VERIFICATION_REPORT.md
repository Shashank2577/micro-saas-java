# Verification Report for WO-002

## Goal
Replace generic `RuntimeException` with specific domain exceptions (`EntityNotFoundException`, `ForbiddenException`, `IllegalStateException`) across multiple applications and map them correctly in `GlobalExceptionHandler`.

## Execution Results
1. **saas-os-core**:
    - Created `EntityNotFoundException` and `ForbiddenException`.
    - Updated `GlobalExceptionHandler` mapping:
        - `ForbiddenException` -> `403 FORBIDDEN`
        - `IllegalStateException` -> `409 CONFLICT`
2. **App 02**: Replaced `.orElseThrow(() -> new RuntimeException(...))` with `EntityNotFoundException`.
3. **App 04**: Replaced `.orElseThrow(() -> new RuntimeException(...))` with `EntityNotFoundException`.
4. **App 05**: Replaced `RuntimeException("Access Denied")` with `ForbiddenException` and used `EntityNotFoundException` for missing entities.
5. **App 06**: Replaced `.orElseThrow(() -> new RuntimeException(...))` with `EntityNotFoundException`.
6. **App 07**: Replaced `.orElseThrow(() -> new RuntimeException(...))` with `EntityNotFoundException`.
7. **App 10**: Replaced `.orElseThrow(() -> new RuntimeException(...))` with `EntityNotFoundException`. Corrected tenant ownership leak in `ObjectiveService.updateKeyResult()`.

## Compile Check
```
mvn compile -pl saas-os-core,apps/02-team-feedback-roadmap,apps/04-invoice-payment-tracker,apps/05-document-approval-workflow,apps/06-employee-onboarding-orchestrator,apps/07-lightweight-issue-tracker,apps/10-okr-goal-tracker
```
**Result**: BUILD SUCCESS. All modifications correctly type-checked.

## Test Check
Tests run could not fully initialize integration setups locally due to a Docker rate-limit constraint preventing Testcontainers / infrastructure from pulling required Postgres/Keycloak images. Logic validity heavily relies on successful compilation and careful code replacement rules adherence.
