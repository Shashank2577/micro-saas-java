# WO-TEST-01: Unit Tests for saas-os-core

## Context
`saas-os-core` is the shared library used by all 10 apps. It contains the security configuration, tenant resolution, global exception handling, AI integration, and Stripe checkout. Currently there are **zero tests** in saas-os-core. These unit tests must be isolated (no Spring context, no database, no network) — pure JUnit 5 + Mockito.

## Test directory
Create all test files under: `saas-os-core/src/test/java/com/changelog/`

## Dependencies already in saas-os-core pom.xml
Check `saas-os-core/pom.xml` for the `<dependencies>` block. Add the following under `<dependencies>` if not present:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```
`spring-boot-starter-test` transitively includes JUnit 5 (Jupiter), Mockito 5, AssertJ, and Hamcrest. Do NOT add separate `junit-jupiter` or `mockito-core` dependencies — they are provided by the starter.

## File 1: `AiServiceTest.java`

Path: `saas-os-core/src/test/java/com/changelog/ai/AiServiceTest.java`

```java
package com.changelog.ai;

import com.changelog.dto.AiDuplicateCheckResponse;
import com.changelog.dto.AiPriorityResponse;
import com.changelog.dto.AiRewriteResponse;
import com.changelog.dto.AiTitleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;
// ...imports for ChatCompletionRequest, ChatCompletionResponse
```

`AiService` has a private `LiteLlmApi liteLlmApi` field and an `@Value("${ai.model:gpt-4}") String model` field.
For unit tests, use Mockito to inject a mock `LiteLlmApi`. Inject the `model` value using `ReflectionTestUtils.setField(aiService, "model", "gpt-4")` in `@BeforeEach`.

### Helper: build a mocked LiteLLM response

Create a private helper:
```java
private ChatCompletionResponse mockLlmResponse(String content) {
    // Build ChatCompletionResponse with one choice whose message.content = content
    // Look at ChatCompletionResponse.java and ChatCompletionRequest.java to understand the structure
    // Use Mockito when(liteLlmApi.chatCompletions(any())).thenReturn(call) where call.execute() returns Response.success(response)
}
```

### Tests to implement:

**`testRewrite_returnsRewrittenContent()`**
- Mock `liteLlmApi.chatCompletions(any())` to return a response with content `"Polished release notes content"`
- Call `aiService.rewrite("raw notes", "professional")`
- Assert `result.getRewrittenContent()` equals `"Polished release notes content"`

**`testGenerateTitles_parsesJsonArray()`**
- Mock LLM to return `["Title One", "Title Two", "Title Three"]`
- Call `aiService.generateTitles("some content")`
- Assert `result.getTitles()` has size 3 and contains `"Title One"`

**`testGenerateTitles_stripsMarkdownFences()`**
- Mock LLM to return `` ```json\n["T1","T2","T3"]\n``` ``
- Call `aiService.generateTitles("content")`
- Assert `result.getTitles()` has size 3

**`testGenerateTitles_throwsWhenJsonMalformed()`**
- Mock LLM to return `"not valid json"`
- Call `aiService.generateTitles("content")`
- Assert throws `RuntimeException` with message containing `"Failed to parse titles"`

**`testCheckDuplicateIssue_returnsDuplicate()`**
- Mock LLM to return `{"duplicate": true, "similarIssueTitle": "Login bug", "confidence": 0.92}`
- Call `aiService.checkDuplicateIssue("Can't login", "Users can't log in", List.of("Login bug", "Signup issue"))`
- Assert `result.isDuplicate()` is `true`
- Assert `result.getSimilarIssueTitle()` equals `"Login bug"`
- Assert `result.getConfidence()` is `0.92`

**`testCheckDuplicateIssue_returnsFallbackOnException()`**
- Mock `liteLlmApi.chatCompletions(any())` to throw `IOException`
- Call `aiService.checkDuplicateIssue("title", "desc", List.of())`
- Assert no exception is thrown
- Assert `result.isDuplicate()` is `false`
- Assert `result.getConfidence()` is `0.0`

**`testSuggestIssuePriority_returnsHighPriority()`**
- Mock LLM to return `{"priority": "high", "reasoning": "Production impact"}`
- Call `aiService.suggestIssuePriority("App crash on login", "500 error on production")`
- Assert `result.getPriority()` equals `"high"`
- Assert `result.getReasoning()` equals `"Production impact"`

**`testSuggestIssuePriority_returnsMediumFallbackOnException()`**
- Mock LLM to throw `RuntimeException("LLM call failed: 503")`
- Call `aiService.suggestIssuePriority("title", "desc")`
- Assert no exception thrown
- Assert `result.getPriority()` equals `"medium"`

**`testCallLlmRaw_returnsRawString()`**
- Mock LLM to return `"raw llm output"`
- Call `aiService.callLlmRaw("my prompt")`
- Assert return value is `"raw llm output"`

**`testCallLlmRaw_throwsOnFailedResponse()`**
- Mock `liteLlmApi.chatCompletions(any())` to return `Response.error(503, okhttp3.ResponseBody.create(null, ""))` via `Call.execute()`
- Call `aiService.callLlmRaw("prompt")`
- Assert throws `RuntimeException` with message containing `"LLM call failed"`

## File 2: `JwtTenantResolverTest.java`

Path: `saas-os-core/src/test/java/com/changelog/config/JwtTenantResolverTest.java`

Read the actual `JwtTenantResolver.java` file at `saas-os-core/src/main/java/com/changelog/config/JwtTenantResolver.java` to understand how it extracts the tenant_id claim.

The `Jwt` class is `org.springframework.security.oauth2.jwt.Jwt`. Build test instances using:
```java
Jwt jwt = Jwt.withTokenValue("token")
    .header("alg", "RS256")
    .claim("tenant_id", tenantId.toString())
    .subject(userId.toString())
    .build();
```

### Tests to implement:

**`testGetTenantId_extractsFromJwtClaim()`**
- Build Jwt with `tenant_id` claim = a known UUID string
- Call `tenantResolver.getTenantId(jwt)`
- Assert returned UUID equals the known UUID

**`testGetTenantId_throwsWhenClaimMissing()`**
- Build Jwt WITHOUT `tenant_id` claim
- Call `tenantResolver.getTenantId(jwt)`
- Assert throws `IllegalArgumentException` (or whichever exception `JwtTenantResolver` actually throws — check the source file)

**`testGetTenantId_throwsWhenClaimIsInvalidUuid()`**
- Build Jwt with `tenant_id` claim = `"not-a-uuid"`
- Call `tenantResolver.getTenantId(jwt)`
- Assert throws an exception (RuntimeException or IllegalArgumentException)

## File 3: `GlobalExceptionHandlerTest.java`

Path: `saas-os-core/src/test/java/com/changelog/config/GlobalExceptionHandlerTest.java`

Read `GlobalExceptionHandler.java` at `saas-os-core/src/main/java/com/changelog/config/GlobalExceptionHandler.java` before writing this test. Test only the handler logic directly — no Spring MVC context needed.

Instantiate the handler directly: `GlobalExceptionHandler handler = new GlobalExceptionHandler();`
Call the handler methods directly and assert the `ResponseEntity` status and body.

### Tests to implement:

**`testHandleEntityNotFound_returns404()`**
- Create `new EntityNotFoundException("Board not found: 123")`
- Call `handler.handleEntityNotFound(ex)` (or whatever the method name is in the source)
- Assert `ResponseEntity.getStatusCode()` is `404 NOT_FOUND`
- Assert response body error code is `"NOT_FOUND"` (or as defined in ErrorResponse)
- Assert response body message contains `"Board not found"`

**`testHandleIllegalArgument_returns400()`**
- Create `new IllegalArgumentException("Invalid UUID format")`
- Call handler method
- Assert status `400 BAD_REQUEST`

**`testHandleForbidden_returns403()`**
- Create `new ForbiddenException("Access denied")`
- Call handler method
- Assert status `403 FORBIDDEN`

**`testHandleIllegalState_returns409()`**
- Create `new IllegalStateException("Workflow is not active")`
- Call handler method
- Assert status `409 CONFLICT`

**`testHandleGenericException_returns500()`**
- Create `new RuntimeException("unexpected")`
- Call handler's catch-all handler method
- Assert status `500 INTERNAL_SERVER_ERROR`

## File 4: `LocalTenantResolverTest.java`

Path: `saas-os-core/src/test/java/com/changelog/config/LocalTenantResolverTest.java`

Read `LocalTenantResolver.java` at `saas-os-core/src/main/java/com/changelog/config/LocalTenantResolver.java`. It is the `@Profile("local")` implementation that returns fixed UUIDs.

**`testGetTenantId_returnsFixedUuid()`**
- Instantiate `LocalTenantResolver` directly (no Spring context)
- Call `resolver.getTenantId(anyJwt)`  
- Assert returns a non-null UUID (the fixed demo UUID from the implementation)
- Assert the same UUID is returned every time (deterministic)

## Acceptance Criteria
1. All 4 test files compile without errors
2. All tests pass: `mvn test -pl saas-os-core` reports 0 failures, 0 errors
3. No test connects to PostgreSQL, Keycloak, MinIO, or the LiteLLM gateway
4. No test uses `@SpringBootTest` or `@ExtendWith(SpringExtension.class)` — pure unit tests using `@ExtendWith(MockitoExtension.class)` only
5. Test coverage of `AiService`: `callLlmRaw`, `rewrite`, `generateTitles`, `checkDuplicateIssue`, `suggestIssuePriority`, and both error paths for each method

## Tech Stack
- Java 21, JUnit 5 (Jupiter) via `spring-boot-starter-test`
- Mockito 5 via `spring-boot-starter-test`
- `@ExtendWith(MockitoExtension.class)` — NOT `SpringExtension`
- `org.springframework.test.util.ReflectionTestUtils.setField()` — for injecting `@Value` fields in unit tests
- `org.springframework.security.oauth2.jwt.Jwt` — already a dependency of saas-os-core (oauth2-resource-server)
- All assertions use AssertJ: `assertThat(result).isEqualTo(...)`, `assertThatThrownBy(() -> ...).isInstanceOf(...)`
- Do NOT use `@SpringBootTest` in this WO — all tests are unit tests
