# SPEC

## Requirements
Implement pure unit tests for `saas-os-core` as described in WO-TEST-01.

These tests need to be fully isolated. We should not start the Spring context (`@SpringBootTest` is not allowed) and should only use Mockito + JUnit 5 (`spring-boot-starter-test` dependency provides these).

## Targeted Files
1. `saas-os-core/src/test/java/com/changelog/ai/AiServiceTest.java`
2. `saas-os-core/src/test/java/com/changelog/config/JwtTenantResolverTest.java`
3. `saas-os-core/src/test/java/com/changelog/config/GlobalExceptionHandlerTest.java`
4. `saas-os-core/src/test/java/com/changelog/config/LocalTenantResolverTest.java`

## Necessary Dependencies
Ensure `spring-boot-starter-test` (scope test) is in `saas-os-core/pom.xml`. The prompt mentions `spring-boot-starter-test` transitively includes JUnit 5 (Jupiter), Mockito 5, AssertJ, and Hamcrest.

## Test Specifics

### 1. `AiServiceTest`
- We will mock `LiteLlmApi` using Mockito.
- Inject `model` value using `ReflectionTestUtils.setField`.
- Add test scenarios for `rewrite`, `generateTitles`, `checkDuplicateIssue`, `suggestIssuePriority`, and `callLlmRaw`, testing both successful execution and exception flows.
- Need to understand `AiDuplicateCheckResponse` and `AiPriorityResponse` fields from `dto` folder, as well as `LiteLlmApi`, `ChatCompletionRequest` and `ChatCompletionResponse` structures.
- From inspecting `AiService.java`:
  - `checkDuplicateIssue` parses to `AiDuplicateCheckResponse`
  - `suggestIssuePriority` parses to `AiPriorityResponse`

### 2. `JwtTenantResolverTest`
- Test `getTenantId` for a valid UUID claim.
- Test missing claim.
- Test invalid UUID claim.
- We will use `Jwt.withTokenValue("token").header("alg", "RS256").claim("tenant_id", "some-uuid").subject("user").build()`.

### 3. `GlobalExceptionHandlerTest`
- Test `handleEntityNotFound`, `handleIllegalArgument`, `handleForbidden`, `handleIllegalState`, and generic `Exception` handlers in `GlobalExceptionHandler.java`.

### 4. `LocalTenantResolverTest`
- Instantiate `LocalTenantResolver` directly. Test `getTenantId(anyJwt)` returns a fixed UUID.

## Implementation Plan
1. Add `spring-boot-starter-test` dependency in `saas-os-core/pom.xml`.
2. Inspect `com.changelog.dto.*` and `com.changelog.ai.*` (like `LiteLlmApi`, `ChatCompletionResponse`) and `com.changelog.config.*` (`JwtTenantResolver`, `GlobalExceptionHandler`, `LocalTenantResolver`) to see exact method names and fields.
3. Write `AiServiceTest`.
4. Write `JwtTenantResolverTest`.
5. Write `GlobalExceptionHandlerTest`.
6. Write `LocalTenantResolverTest`.
7. Run `mvn test -pl saas-os-core`.
