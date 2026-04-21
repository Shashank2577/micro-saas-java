# WO-TEST-02: Integration Tests — App 09 (Changelog Platform) + App 07 (Issue Tracker)

## Context
These two apps are the most complete reference implementations in the monorepo. App 09 (`apps/09-changelog-platform`) already has some test files but they are incomplete. App 07 (`apps/07-lightweight-issue-tracker`) has no tests. Both apps make real LiteLLM gateway calls — for tests, the gateway must be mocked.

The test pattern to follow is already established in `apps/09-changelog-platform/src/test/java/com/changelog/api/ProjectControllerTest.java` — read this file carefully before writing any tests. The key patterns are:
- `@SpringBootTest + @AutoConfigureMockMvc` on the class
- `@MockBean private JwtDecoder jwtDecoder` to prevent JWT validation against Keycloak
- JWT injection via `.with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString())))`
- Direct DB seeding via `@Autowired JdbcTemplate jdbcTemplate` in `@BeforeEach`
- Insert into `cc.tenants` before inserting app-specific data (FK constraint)
- Clean up with `@AfterEach` via `jdbcTemplate.update("DELETE FROM ...")`

**Important**: Tests connect to the REAL PostgreSQL database (localhost:5433, database `changelog`). There are NO Testcontainers in this project — that library is broken on the current machine. Tests require the database to be running. The `cc.tenants` and `cc.users` tables must exist (created by App 09's Flyway migration V1).

## App 09 — Changelog Platform

### Test dependency check
Read `apps/09-changelog-platform/pom.xml`. Ensure these are present under `<dependencies>`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

### Test resources
Create `apps/09-changelog-platform/src/test/resources/application-test.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/changelog
spring.datasource.username=changelog
spring.datasource.password=changelog
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=false
ai.gateway-url=http://localhost:4000
minio.endpoint=http://localhost:9000
stripe.secret-key=sk_test_placeholder
```
Annotate all test classes with `@ActiveProfiles("test")`.

### File 1: `ChangelogPostControllerTest.java`

Path: `apps/09-changelog-platform/src/test/java/com/changelog/api/ChangelogPostControllerTest.java`

Read the actual controller at `apps/09-changelog-platform/src/main/java/com/changelog/api/` and the `Post` model to get field names.

**Setup `@BeforeEach`:**
- Insert row into `cc.tenants(id, name, slug, plan_tier)`
- Insert row into `projects(id, tenant_id, name, slug)` 
- Insert row into `posts(id, tenant_id, project_id, title, content, status)` with status `"draft"`

**Tests:**

`testListPosts_returnsOnlyCurrentTenantPosts()`
- GET `/api/v1/posts` with JWT for `tenantId`
- Assert status 200
- Assert JSON array contains the seeded post
- Assert JSON array does NOT contain posts from another tenant (insert one with a different `tenantId` and verify it's absent)

`testGetPost_returnsPostById()`
- GET `/api/v1/posts/{postId}` with JWT
- Assert status 200
- Assert `$.title` matches the seeded value

`testGetPost_returns404ForUnknownId()`
- GET `/api/v1/posts/{randomUUID}` with JWT
- Assert status 404

`testCreatePost_persistsAndReturns201()`
- POST `/api/v1/posts` with JWT
- Body: `{"title": "New Release", "content": "What changed", "projectId": "{projectId}", "status": "draft"}`
- Assert status 201 (or 200 — check what the controller returns)
- Assert `$.title` = `"New Release"`
- Assert `$.tenantId` = `tenantId.toString()`
- Query DB to verify row was inserted

`testPublishPost_changesStatusToPublished()`
- POST or PATCH `/api/v1/posts/{postId}/publish` with JWT
- Assert status 200
- Assert `$.status` = `"published"` (or whatever the published state field is called)
- Query DB to verify `status` column changed

`testCreatePost_returns400WhenTitleMissing()`
- POST `/api/v1/posts` with JWT
- Body: `{"content": "missing title"}`
- Assert status 400

`testCreatePost_returns401WithoutJwt()`
- POST `/api/v1/posts` WITHOUT JWT
- Assert status 401

`testTenantIsolation_cannotReadOtherTenantPost()`
- Seed a post for `tenantA`
- GET `/api/v1/posts/{tenantA_postId}` with JWT for `tenantB`
- Assert status 404 (not 403 — don't leak existence)

### File 2: `AiControllerTest.java`

Path: `apps/09-changelog-platform/src/test/java/com/changelog/ai/AiControllerTest.java`

The AI endpoints call `AiService` which calls `LiteLlmApi` via Retrofit. For tests, mock the `LiteLlmApi` bean:
```java
@MockBean
private com.changelog.ai.LiteLlmApi liteLlmApi;
```

Before each test, configure the mock to return a valid `ChatCompletionResponse` so LLM calls succeed.

**Tests:**

`testRewritePost_returnsRewrittenContent()`
- Mock `liteLlmApi.chatCompletions(any())` to return a successful response with content `"Professional rewritten notes"`
- POST `/api/v1/ai/rewrite` with JWT
- Body: `{"postId": "{postId}", "tone": "professional"}` (check actual endpoint/body fields in the controller)
- Assert status 200
- Assert response contains the mocked rewritten content

`testGenerateTitles_returnsTitleOptions()`
- Mock LLM to return `["Title A", "Title B", "Title C"]`
- POST `/api/v1/ai/titles` with JWT and `{"postId": "{postId}"}`
- Assert status 200
- Assert response has 3 title options

`testAiEndpoint_returns200WithFallbackWhenGatewayDown()`
- Mock `liteLlmApi.chatCompletions(any())` to throw `RuntimeException("Connection refused")`
- POST to the AI rewrite endpoint
- Assert status is NOT 500 — must be 200 with an error message or graceful fallback

### File 3: `PublicChangelogControllerTest.java`

Path: `apps/09-changelog-platform/src/test/java/com/changelog/api/PublicChangelogControllerTest.java`

The public changelog endpoint is unauthenticated. Find it by searching for `@GetMapping` in the `public` or `changelog` controller package.

**Tests:**

`testPublicChangelog_returnsPublishedPostsOnly()`
- Seed one published post and one draft post for the same project
- GET `/public/changelog/{projectSlug}` (check the actual URL in the controller) WITHOUT JWT
- Assert status 200
- Assert response contains the published post
- Assert response does NOT contain the draft post

`testPublicChangelog_returns404ForUnknownSlug()`
- GET `/public/changelog/does-not-exist` WITHOUT JWT
- Assert status 404 (or 200 with empty list — check actual behavior)

---

## App 07 — Lightweight Issue Tracker

### Test dependency check
Read `apps/07-lightweight-issue-tracker/pom.xml`. Add if missing:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

### Test resources
Create `apps/07-lightweight-issue-tracker/src/test/resources/application-test.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/changelog
spring.datasource.username=changelog
spring.datasource.password=changelog
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=false
ai.gateway-url=http://localhost:4000
minio.endpoint=http://localhost:9000
stripe.secret-key=sk_test_placeholder
```

### File 4: `IssueControllerTest.java`

Path: `apps/07-lightweight-issue-tracker/src/test/java/com/changelog/issuetracker/IssueControllerTest.java`

Read the `Issue` model and `IssueController` to understand field names and endpoint URLs.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert `projects(id, tenant_id, name, slug, status)` row (find actual table/column names in the migration SQL at `apps/07-lightweight-issue-tracker/src/main/resources/db/migration/V1__*.sql`)
- Insert one `issues` row with status `"open"`, priority `"medium"`

**Mock LiteLlmApi:**
```java
@MockBean
private com.changelog.ai.LiteLlmApi liteLlmApi;
```
Configure it in `@BeforeEach` to return a valid response so the AI duplicate/priority check doesn't fail.

**Tests:**

`testListIssues_returnsProjectIssues()`
- GET `/api/v1/projects/{projectId}/issues` with JWT for `tenantId`
- Assert status 200
- Assert JSON array has 1 element (the seeded issue)

`testCreateIssue_persistsWithCorrectTenantId()`
- POST `/api/v1/projects/{projectId}/issues` with JWT
- Body: `{"title": "Bug report", "description": "App crashes", "priority": "high"}`
- Assert status 201 (or 200)
- Assert `$.tenantId` = `tenantId.toString()`
- Query DB to verify row inserted

`testCreateIssue_returns400WhenTitleMissing()`
- POST with body `{"description": "no title"}`
- Assert status 400

`testGetIssue_returns404ForOtherTenantIssue()`
- Seed an issue for `tenantA`
- GET that issue with JWT for `tenantB`
- Assert status 404

`testUpdateIssueStatus_changesStatus()`
- PATCH or PUT `/api/v1/issues/{issueId}` with JWT
- Body: `{"status": "closed"}`
- Assert status 200
- Assert `$.status` = `"closed"`

`testAddComment_persistsComment()`
- POST `/api/v1/issues/{issueId}/comments` with JWT
- Body: `{"content": "Looking into this"}`
- Assert status 201 (or 200)
- Assert `$.content` = `"Looking into this"`

`testAiDuplicateCheck_doesNotReturn500WhenGatewayDown()`
- Mock `liteLlmApi.chatCompletions(any())` to throw `RuntimeException`
- POST to create a new issue
- Assert status is NOT 500 (issue creation succeeds despite AI failure)

### File 5: `ProjectControllerTest.java` (App 07)

Path: `apps/07-lightweight-issue-tracker/src/test/java/com/changelog/issuetracker/ProjectControllerTest.java`

**Tests:**

`testCreateProject_returnsCreatedProject()`
- POST `/api/v1/projects` with JWT
- Body: `{"name": "Backend", "key": "BE"}` (check actual fields from migration SQL)
- Assert status 201 (or 200)
- Assert `$.tenantId` = `tenantId.toString()`

`testListProjects_returnsOnlyTenantProjects()`
- GET `/api/v1/projects` with JWT
- Assert returns only the seeded project (not projects of other tenants)

## Acceptance Criteria
1. All 5 test files compile without errors
2. All tests pass when PostgreSQL is running at `localhost:5433` with the `changelog` database: `mvn test -pl apps/09-changelog-platform,apps/07-lightweight-issue-tracker` reports 0 failures
3. Tests that call AI endpoints correctly mock `LiteLlmApi` — no real HTTP calls to LiteLLM gateway
4. `@MockBean private JwtDecoder jwtDecoder` is present in every test class (prevents Keycloak calls)
5. `@ActiveProfiles("test")` is on every test class
6. Each test class has `@AfterEach` that deletes all rows inserted by that test class (clean state per test run)
7. Tenant isolation tests (reading another tenant's data) return 404, not 403 (service layer filters by tenantId)

## Tech Stack
- Java 21, Spring Boot 3.3.5
- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` or `@SpringBootTest + @AutoConfigureMockMvc`
- `spring-security-test`: `SecurityMockMvcRequestPostProcessors.jwt()` for JWT injection
- `@MockBean` for `JwtDecoder` (blocks Keycloak) and `LiteLlmApi` (blocks gateway)
- JUnit 5 Jupiter: `@Test`, `@BeforeEach`, `@AfterEach`
- MockMvc: `mockMvc.perform(get(...).with(jwt(...))).andExpect(status().isOk())`
- AssertJ + Hamcrest matchers via `spring-boot-starter-test`
- Real PostgreSQL at `localhost:5433` — NO Testcontainers
