# WO-002: Fix Error Handling — Domain Exceptions Instead of RuntimeException

## Problem
Currently, the shared `GlobalExceptionHandler` maps `EntityNotFoundException` to 404, but many services throw a raw `RuntimeException` which translates to 500. Additionally, access denied is thrown as `RuntimeException` resulting in 500 instead of 403.

## What's Missing
1. A `ForbiddenException` mapped to 403
2. An `IllegalStateException` handler mapped to 409
3. Correct usage of `EntityNotFoundException` in all service `orElseThrow()` calls

## Requirements
1. Create `EntityNotFoundException` and `ForbiddenException` in `saas-os-core` if not already present.
2. Update `GlobalExceptionHandler` in `saas-os-core` to map `ForbiddenException` to 403 and `IllegalStateException` to 409.
3. Update `apps/02-team-feedback-roadmap` services to throw `EntityNotFoundException`.
4. Update `apps/04-invoice-payment-tracker` services to throw `EntityNotFoundException`.
5. Update `apps/05-document-approval-workflow` services to throw `EntityNotFoundException`, `ForbiddenException`, and `IllegalStateException`.
6. Update `apps/06-employee-onboarding-orchestrator` services to throw `EntityNotFoundException`.
7. Update `apps/07-lightweight-issue-tracker` services to throw `EntityNotFoundException`.
8. Update `apps/10-okr-goal-tracker` services to throw `EntityNotFoundException` and handle tenant ownership logic.
