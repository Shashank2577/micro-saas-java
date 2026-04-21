# WO-001: Fix Tenant Isolation Across All Apps

## Problem
Three apps use insecure tenant ID extraction patterns instead of the standard `TenantResolver` bean from `saas-os-core`. This allows any authenticated user to pass any UUID in a header and access any tenant's data.

### Affected Files

**App 01 — Silent fallback UUID bug:**
- `apps/01-client-portal-builder/src/main/java/com/changelog/business/portals/controller/PortalController.java`
  - Has a private `getTenantId(JwtAuthenticationToken)` method (lines ~29–35) with fallback to hardcoded UUID `550e8400-e29b-41d4-a716-446655440000`
  - If the JWT has no `tenant_id` claim, it silently returns all data for that demo UUID instead of rejecting the request

**App 03 — Header-based tenant ID:**
- `apps/03-ai-knowledge-base/src/main/java/com/changelog/controller/SpaceController.java`
- `apps/03-ai-knowledge-base/src/main/java/com/changelog/controller/KbPageController.java`
- `apps/03-ai-knowledge-base/src/main/java/com/changelog/controller/SearchController.java`
- `apps/03-ai-knowledge-base/src/main/java/com/changelog/controller/AiQaController.java`
  - All use `@RequestHeader("X-Tenant-ID") UUID tenantId` — completely bypasses JWT for tenant resolution

**App 06 — Header-based tenant ID:**
- `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/controller/OnboardingTemplateController.java`
- `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/controller/OnboardingInstanceController.java`
- `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/controller/OnboardingPortalController.java`
- `apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/controller/OnboardingTaskController.java`
  - All use `@RequestHeader("X-Tenant-ID") UUID tenantId` and `@RequestHeader("X-User-ID") UUID userId`

**App 10 — Missing tenant guard on one endpoint:**
- `apps/10-okr-goal-tracker/src/main/java/com/changelog/okr/controller/OkrController.java`
  - Method `updateKeyResult()` at line ~104: calls `objectiveService.updateKeyResult(krId, updates)` with no tenantId passed — any tenant can update any key result if they know the UUID

## Standard Pattern to Follow
Look at App 07 as the reference implementation:
- `apps/07-lightweight-issue-tracker/src/main/java/com/changelog/issuetracker/controller/IssueController.java`
- `apps/07-lightweight-issue-tracker/src/main/java/com/changelog/issuetracker/service/IssueService.java`

The correct pattern is:
```java
// Controller — inject TenantResolver, accept Jwt from Spring Security
private final IssueService issueService;
private final TenantResolver tenantResolver;  // from saas-os-core: com.changelog.config.TenantResolver

@GetMapping
public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt) {
    UUID tenantId = tenantResolver.getTenantId(jwt);  // reads "tenant_id" claim, throws if absent
    return ResponseEntity.ok(issueService.listIssues(tenantId));
}
```

The `TenantResolver` interface is at `saas-os-core/src/main/java/com/changelog/config/TenantResolver.java`.
The production implementation is `JwtTenantResolver` (`saas-os-core/.../config/JwtTenantResolver.java`).

For userId, use `jwt.getSubject()` directly (the Keycloak sub claim is the user UUID).

## What to Do

### 1. Fix App 01 — PortalController
- Remove the private `getTenantId()` method with the hardcoded fallback
- Add `TenantResolver tenantResolver` as a constructor-injected dependency
- Replace all calls to the private method with `tenantResolver.getTenantId(jwt)`
- Add `@AuthenticationPrincipal Jwt jwt` parameter to all controller methods that need tenantId
- If `tenant_id` claim is missing, `JwtTenantResolver.getTenantId()` will throw `IllegalArgumentException` which is caught by `GlobalExceptionHandler` and returns 400 — that is the correct behaviour

### 2. Fix App 03 — All controllers
- Remove ALL `@RequestHeader("X-Tenant-ID") UUID tenantId` parameters
- Add `TenantResolver tenantResolver` as a constructor-injected dependency to each controller
- Add `@AuthenticationPrincipal Jwt jwt` to each endpoint method
- Replace `tenantId` resolution with `tenantResolver.getTenantId(jwt)`
- For the unauthenticated public search endpoint (if any), keep it unauthenticated but do NOT pass tenantId to restrict results — instead accept `?tenantSlug=` query param and resolve via the `TenantSlug` entity (see App 02's `PublicFeedbackController` for the pattern)

### 3. Fix App 06 — All controllers
- Same as App 03 above
- For userId resolution: replace `@RequestHeader("X-User-ID") UUID userId` with `UUID userId = UUID.fromString(jwt.getSubject())`
- The `OnboardingPortalController` has an unauthenticated path (portal token access) — for that path, userId should be null or derived from the token, not from a JWT

### 4. Fix App 10 — OkrController updateKeyResult
- Read the `updateKeyResult` method signature in `OkrController` and `ObjectiveService`
- Add `UUID tenantId = tenantResolver.getTenantId(jwt)` in the controller method
- Pass `tenantId` to `objectiveService.updateKeyResult(tenantId, krId, updates)`
- In `ObjectiveService.updateKeyResult()`, add a check: load the KeyResult, verify its Objective belongs to a Cycle owned by the given tenantId, throw `EntityNotFoundException` (from `com.changelog.exception.EntityNotFoundException`) if not found or if tenant doesn't match

## Acceptance Criteria
1. No controller in any app uses `@RequestHeader("X-Tenant-ID")` for tenant resolution
2. No controller in any app has a hardcoded fallback UUID for tenant resolution
3. All tenant IDs are extracted from the JWT via `TenantResolver.getTenantId(jwt)`
4. `PUT /api/key-results/{krId}` in App 10 verifies the key result belongs to the requesting tenant
5. Passing a JWT from tenant A while trying to access tenant B's resources returns 404 (not found by tenantId filter) or 403 (explicit check)
6. App compiles and existing startup passes: `mvn compile -pl apps/01-client-portal-builder,apps/03-ai-knowledge-base,apps/06-employee-onboarding-orchestrator,apps/10-okr-goal-tracker`

## Tech Stack
- Java 21, Spring Boot 3.3.5
- Spring Security OAuth2 Resource Server — `@AuthenticationPrincipal Jwt` is from `org.springframework.security.oauth2.jwt.Jwt`
- `TenantResolver` bean is registered in saas-os-core as `@Component` under profile `!local`
- `LocalTenantResolver` is used under `@Profile("local")` and returns fixed UUIDs — this means tests running under local profile will still work without real JWTs
- Do NOT add `@Profile` annotations to controllers — only the `TenantResolver` implementation is profile-specific
