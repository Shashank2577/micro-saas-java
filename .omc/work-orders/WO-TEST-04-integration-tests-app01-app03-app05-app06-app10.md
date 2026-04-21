# WO-TEST-04: Integration Tests — Apps 01, 03, 05, 06, 10

## Context
Five remaining apps: client portal builder (01), AI knowledge base (03), document approval workflow (05), employee onboarding (06), and OKR goal tracker (10). Some of these have complex workflows (multi-step approval, onboarding lifecycle).

The test pattern to follow is established in `apps/09-changelog-platform/src/test/java/com/changelog/api/ProjectControllerTest.java`. Read this file before writing any tests. Key patterns:
- `@SpringBootTest + @AutoConfigureMockMvc`
- `@MockBean private JwtDecoder jwtDecoder` prevents Keycloak calls
- JWT injection: `.with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString())))`
- `@Autowired JdbcTemplate jdbcTemplate` for DB seeding
- Insert `cc.tenants` row first (FK constraint on all app tables)
- `@AfterEach` cleans up rows

**Database**: Real PostgreSQL at `localhost:5433`, database `changelog`. Flyway DISABLED in tests.

---

## App 01 — Client Portal Builder

### Test dependencies
Read `apps/01-client-portal-builder/pom.xml`. Add if missing:
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
Create `apps/01-client-portal-builder/src/test/resources/application-test.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/changelog
spring.datasource.username=changelog
spring.datasource.password=changelog
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=false
minio.endpoint=http://localhost:9000
stripe.secret-key=sk_test_placeholder
ai.gateway-url=http://localhost:4000
```

### File 1: `PortalControllerTest.java`

Path: `apps/01-client-portal-builder/src/test/java/com/changelog/portals/PortalControllerTest.java`

Read `apps/01-client-portal-builder/src/main/java/com/changelog/business/portals/controller/PortalController.java` and the migration SQL to understand table/column names.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert one `portals(id, tenant_id, name, slug, status)` row (find exact columns from migration)

**Tests:**

`testListPortals_returnsTenantPortals()`
- GET `/api/v1/portals` with JWT
- Assert status 200
- Assert seeded portal is in response

`testCreatePortal_persistsCorrectly()`
- POST `/api/v1/portals` with JWT
- Body: use fields from the actual `CreatePortalRequest` DTO — read that file. Minimum: `{"name": "Client Portal", "slug": "client-portal"}`
- Assert status 201 or 200
- Assert `$.tenantId` = `tenantId.toString()`

`testCreatePortal_returns400WhenNameMissing()`
- POST with empty body `{}`
- Assert status 400

`testGetPortal_returns404ForOtherTenantPortal()`
- GET the seeded portal with JWT from a different `tenantB`
- Assert status 404

`testTenantIsolation_noFallbackUuid()`
- Build a JWT WITHOUT `tenant_id` claim
- GET `/api/v1/portals` with that JWT
- Assert status 400 (NOT 200 with the demo tenant's data)
- This test specifically verifies the hardcoded fallback UUID bug (WO-001) was fixed — after WO-001 is applied, missing tenant_id must return 400 not silently use the demo UUID

---

## App 03 — AI Knowledge Base

### Test dependencies
Read `apps/03-ai-knowledge-base/pom.xml`. Add if missing:
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
Create `apps/03-ai-knowledge-base/src/test/resources/application-test.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/changelog
spring.datasource.username=changelog
spring.datasource.password=changelog
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=false
minio.endpoint=http://localhost:9000
stripe.secret-key=sk_test_placeholder
ai.gateway-url=http://localhost:4000
ai.embedding-model=text-embedding-3-small
```

**Important for App 03**: The `EmbeddingService` calls the LiteLLM gateway for embeddings. Mock BOTH `LiteLlmApi` AND the Retrofit-based embedding call:
```java
@MockBean
private com.changelog.ai.LiteLlmApi liteLlmApi;
```
For embedding calls, mock `liteLlmApi.createEmbedding(any())` to return a `Call<EmbeddingResponse>` where the response contains a `float[]` of 1536 zeroes (a valid but meaningless embedding for test purposes).

### File 2: `KbPageControllerTest.java`

Path: `apps/03-ai-knowledge-base/src/test/java/com/changelog/KbPageControllerTest.java`

Read the migration SQL at `apps/03-ai-knowledge-base/src/main/resources/db/migration/V1__*.sql` for table/column names. Read `KbPageController.java` and `KbPage.java`.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert `spaces(id, tenant_id, name, slug)` row (or whatever the space table is called)
- Insert `kb_pages(id, tenant_id, space_id, title, content)` row

Configure mock LiteLlmApi to return a valid embedding (1536-element float array with all 0.0f values):
```java
// In @BeforeEach:
EmbeddingResponse embeddingResponse = ...; // build with 1536 zeroes
retrofit2.Call<EmbeddingResponse> call = mock(retrofit2.Call.class);
when(call.execute()).thenReturn(retrofit2.Response.success(embeddingResponse));
when(liteLlmApi.createEmbedding(any())).thenReturn(call);
```

**Tests:**

`testListPages_returnsTenantPages()`
- GET `/api/v1/pages` or `/api/v1/spaces/{spaceId}/pages` (check actual URL in controller)
- Assert status 200, contains seeded page

`testCreatePage_triggersEmbeddingGeneration()`
- POST the create page endpoint with JWT
- Body: `{"title": "API Documentation", "content": "This is documentation about our REST API...", "spaceId": "{spaceId}"}`
- Assert status 201 or 200
- Verify `liteLlmApi.createEmbedding(any())` was called (Mockito `verify(liteLlmApi, atLeastOnce()).createEmbedding(any())`)
- Query DB: assert at least 1 row in `page_chunks` with `page_id = {newPageId}`

`testUpdatePage_retrigggersEmbedding()`
- PUT `/api/v1/pages/{pageId}` with new content
- Verify `liteLlmApi.createEmbedding(any())` was called again
- Query DB: assert old chunks were deleted and new chunks inserted

`testCreatePage_succeedsEvenWhenEmbeddingFails()`
- Mock `liteLlmApi.createEmbedding(any())` to throw `RuntimeException("gateway down")`
- POST create page
- Assert status is NOT 500 — page creation must succeed with graceful degradation
- Query DB: assert page row exists (but no chunks — that's OK)

### File 3: `SearchControllerTest.java`

Path: `apps/03-ai-knowledge-base/src/test/java/com/changelog/SearchControllerTest.java`

Read `SearchController.java` to understand keyword vs semantic search paths.

**Tests:**

`testKeywordSearch_returnsMatchingPages()`
- Seed a page with title "Authentication Guide" and content containing "OAuth tokens"
- GET `/api/v1/search?q=OAuth&type=keyword` with JWT
- Assert status 200
- Assert response contains the seeded page

`testSemanticSearch_callsEmbeddingService()`
- Mock embedding to return 1536 zeroes
- GET `/api/v1/search?q=authentication&type=semantic` with JWT
- Assert status 200
- Verify `liteLlmApi.createEmbedding(any())` was called once

`testSemanticSearch_fallsBackGracefullyWhenGatewayDown()`
- Mock `liteLlmApi.createEmbedding(any())` to throw
- GET `/api/v1/search?q=test&type=semantic` with JWT
- Assert status 200 (graceful degradation, not 500)

### File 4: `AiQaControllerTest.java`

Path: `apps/03-ai-knowledge-base/src/test/java/com/changelog/AiQaControllerTest.java`

Read `AiQaController.java` to understand the Q&A endpoint URL and request body format.

**Tests:**

`testAskQuestion_returnsNonHardcodedAnswer()`
- Mock embedding to return 1536 zeroes
- Mock `liteLlmApi.chatCompletions(any())` to return a response with content `"Based on the documentation, OAuth tokens expire after 1 hour."`
- POST `/api/v1/ai/ask` with JWT
- Body: `{"question": "How long do OAuth tokens last?"}` (check actual request field name)
- Assert status 200
- Assert response answer equals `"Based on the documentation, OAuth tokens expire after 1 hour."`
- Assert response is NOT the old hardcoded stub text

`testAskQuestion_returns200WithFallbackWhenGatewayDown()`
- Mock both `createEmbedding` and `chatCompletions` to throw
- POST `/api/v1/ai/ask`
- Assert status 200 (not 500)
- Assert answer contains `"unavailable"` or `"I don't have"` or similar fallback text

---

## App 05 — Document Approval Workflow

### Test dependencies
Read `apps/05-document-approval-workflow/pom.xml`. Add if missing:
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
Create `apps/05-document-approval-workflow/src/test/resources/application-test.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/changelog
spring.datasource.username=changelog
spring.datasource.password=changelog
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=false
minio.endpoint=http://localhost:9000
stripe.secret-key=sk_test_placeholder
ai.gateway-url=http://localhost:4000
```

### File 5: `DocumentControllerTest.java`

Path: `apps/05-document-approval-workflow/src/test/java/com/changelog/approval/DocumentControllerTest.java`

Read `apps/05-document-approval-workflow/src/main/resources/db/migration/V1__*.sql` for table/column names. Read `DocumentController.java` and `DocumentService.java`.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert `documents(id, tenant_id, title, content, status, created_by)` row with status `"draft"`
- `created_by` = `userId` UUID

**Tests:**

`testListDocuments_returnsTenantDocuments()`
- GET `/api/v1/documents` with JWT
- Assert status 200, contains seeded document

`testCreateDocument_persistsWithDraftStatus()`
- POST `/api/v1/documents` with JWT
- Body: `{"title": "Q4 Report", "content": "Financial summary..."}` (check actual DTO fields)
- Assert status 201 or 200
- Assert `$.status` = `"draft"`

`testGetDocument_returns403ForNonOwnerNonApprover()`
- Seed a document owned by `userId`
- GET the document with JWT for a different `otherUserId` (same tenant)
- Read `DocumentService.java` lines around "access denied" — if it throws `ForbiddenException`, assert status 403
- If the service doesn't check ownership (only tenancy), assert 200 instead — match actual behavior

`testSubmitForReview_changesStatusToPendingApproval()`
- POST or PATCH the submit endpoint (find in DocumentController)
- Assert status 200
- Assert `$.status` = `"pending_approval"` (or equivalent)

### File 6: `WorkflowControllerTest.java`

Path: `apps/05-document-approval-workflow/src/test/java/com/changelog/approval/WorkflowControllerTest.java`

Read `WorkflowService.java` to understand the approval step logic.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert `documents` row with status `"pending_approval"`
- Insert `approval_workflows(id, tenant_id, document_id, status)` row
- Insert `approval_steps` rows if required (check migration)

**Tests:**

`testApproveDocument_changesStatusToApproved()`
- POST the approval endpoint with JWT as the approver
- Assert status 200
- Assert document status changed to `"approved"` (query DB or check response)

`testRejectDocument_changesStatusToRejected()`
- POST the rejection endpoint with a rejection reason
- Assert status 200
- Assert document `$.status` = `"rejected"`

`testApprove_returns409WhenWorkflowNotActive()`
- Set up a document with status `"approved"` (already done)
- Attempt to approve again
- Assert status 409 CONFLICT (the `IllegalStateException` → 409 mapping from WO-002)

---

## App 06 — Employee Onboarding Orchestrator

### Test dependencies
Read `apps/06-employee-onboarding-orchestrator/pom.xml`. Add if missing:
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
Create `apps/06-employee-onboarding-orchestrator/src/test/resources/application-test.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/changelog
spring.datasource.username=changelog
spring.datasource.password=changelog
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=false
minio.endpoint=http://localhost:9000
stripe.secret-key=sk_test_placeholder
ai.gateway-url=http://localhost:4000
```

**Important for App 06**: `AiOnboardingService` calls `AiService` which calls `LiteLlmApi`. Mock `LiteLlmApi` to prevent real AI calls:
```java
@MockBean
private com.changelog.ai.LiteLlmApi liteLlmApi;
```
Configure it in `@BeforeEach` to return a JSON array of 3 tasks when `chatCompletions` is called.

### File 7: `OnboardingTemplateControllerTest.java`

Path: `apps/06-employee-onboarding-orchestrator/src/test/java/com/changelog/OnboardingTemplateControllerTest.java`

Read the migration SQL at `apps/06-employee-onboarding-orchestrator/src/main/resources/db/migration/V1__*.sql`. Read `OnboardingTemplateController.java` and the template model.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert `onboarding_templates(id, tenant_id, name, description, category, is_active)` row

**Tests:**

`testListTemplates_returnsTenantTemplates()`
- GET `/api/v1/onboarding/templates` (check actual URL) with JWT
- Assert status 200, contains seeded template

`testCreateTemplate_persistsCorrectly()`
- POST templates endpoint with JWT
- Body: `{"name": "Engineering Onboarding", "category": "onboarding"}`
- Assert status 201 or 200
- Assert `$.tenantId` = `tenantId.toString()`

`testAiGeneratePlan_returnsTemplateWithTasks()`
- Mock LLM to return a JSON array of 3 task objects:
  ```json
  [{"title":"Read handbook","description":"Review policies","taskType":"read","assigneeType":"new_hire","dueDayOffset":0,"isRequired":true},
   {"title":"IT setup","description":"Configure laptop","taskType":"complete","assigneeType":"it","dueDayOffset":0,"isRequired":true},
   {"title":"Manager meeting","description":"Align on goals","taskType":"schedule_meeting","assigneeType":"manager","dueDayOffset":1,"isRequired":true}]
  ```
- POST to the AI generate plan endpoint (check controller — likely `/api/v1/onboarding/ai/generate-plan?jobTitle=Engineer&department=Engineering`)
- Assert status 200
- Assert `$.tasks` has 3 elements
- Assert first task title = `"Read handbook"`

`testAiGeneratePlan_returns200WithDefaultTasksWhenGatewayDown()`
- Mock `liteLlmApi.chatCompletions(any())` to throw `RuntimeException`
- POST the AI generate plan endpoint
- Assert status 200 (graceful fallback, not 500)
- Assert `$.tasks` has at least 1 task (the default 3 fallback tasks)

### File 8: `OnboardingInstanceControllerTest.java`

Path: `apps/06-employee-onboarding-orchestrator/src/test/java/com/changelog/OnboardingInstanceControllerTest.java`

Read `OnboardingInstanceController.java` and `OnboardingInstanceService.java`.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert `onboarding_templates` row
- Insert template tasks rows
- Insert `onboarding_instances(id, tenant_id, template_id, hire_name, hire_email, hire_role, start_date, status, portal_token)` row

**Tests:**

`testStartOnboarding_createsInstanceWithTasks()`
- POST `/api/v1/onboarding/instances` with JWT
- Body: `{"templateId": "{templateId}", "hireName": "Jane Doe", "hireEmail": "jane@example.com", "hireRole": "Engineer", "startDate": "2026-05-01"}`
- Assert status 201 or 200
- Assert `$.tasks` is non-empty (tasks were copied from template)

`testGetInstanceByPortalToken_worksWithoutAuth()`
- GET `/api/v1/onboarding/portal/{portalToken}` WITHOUT JWT (unauthenticated portal access)
- Assert status 200
- Assert `$.hireName` = seeded hire name

`testCancelOnboarding_changesStatusToCancelled()`
- POST or PATCH the cancel endpoint with JWT
- Assert status 200
- Assert `$.status` = `"cancelled"`

---

## App 10 — OKR Goal Tracker

### Test dependencies
Read `apps/10-okr-goal-tracker/pom.xml`. Add if missing:
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
Create `apps/10-okr-goal-tracker/src/test/resources/application-test.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/changelog
spring.datasource.username=changelog
spring.datasource.password=changelog
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=false
minio.endpoint=http://localhost:9000
stripe.secret-key=sk_test_placeholder
ai.gateway-url=http://localhost:4000
```

### File 9: `OkrControllerTest.java`

Path: `apps/10-okr-goal-tracker/src/test/java/com/changelog/okr/OkrControllerTest.java`

Read the migration SQL at `apps/10-okr-goal-tracker/src/main/resources/db/migration/V1__*.sql`. Read `OkrController.java`, `OkrCycleService.java`, `ObjectiveService.java`.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert `okr_cycles(id, tenant_id, name, start_date, end_date, status)` row with status `"active"`
- Insert `objectives(id, tenant_id, cycle_id, title, status)` row
- Insert `key_results(id, tenant_id, objective_id, title, target_value, current_value, unit)` row

**Tests:**

`testListCycles_returnsTenantCycles()`
- GET the OKR cycles list endpoint with JWT
- Assert status 200, contains seeded cycle

`testCreateObjective_persistsCorrectly()`
- POST the objective creation endpoint with JWT
- Body: `{"cycleId": "{cycleId}", "title": "Improve API reliability"}` (check actual DTO)
- Assert status 201 or 200
- Assert `$.tenantId` = `tenantId.toString()`

`testUpdateKeyResult_updatesProgress()`
- PATCH or PUT `/api/key-results/{krId}` with JWT
- Body: `{"currentValue": 75.0}` (check actual update DTO fields in controller/service)
- Assert status 200
- Assert `$.currentValue` = `75.0`

`testUpdateKeyResult_returns404ForOtherTenantKr()`
- Seed a KR for `tenantA`
- PUT that KR with JWT for `tenantB`
- Assert status 404 (tenant guard from WO-001 fix — after WO-001 is applied, cross-tenant KR updates return 404)

`testCreateObjective_returns404ForInvalidCycleId()`
- POST objective with `cycleId` that doesn't exist (random UUID)
- Assert status 404

`testCheckIn_updatesKeyResultProgress()`
- Find the check-in endpoint (if it exists in OkrController — search for `checkIn` or `progress` in the controller)
- If it exists: POST check-in with progress value
- Assert the KR's progress was updated

## Acceptance Criteria
1. All 9 test files compile without errors
2. All tests pass when PostgreSQL is running: `mvn test -pl apps/01-client-portal-builder,apps/03-ai-knowledge-base,apps/05-document-approval-workflow,apps/06-employee-onboarding-orchestrator,apps/10-okr-goal-tracker` reports 0 failures
3. `@MockBean JwtDecoder jwtDecoder` in every authenticated test class
4. `@ActiveProfiles("test")` on every test class
5. `@AfterEach` cleans up all seeded rows
6. App 01 test specifically verifies that missing `tenant_id` JWT claim returns 400 (not 200 with hardcoded UUID)
7. App 03 tests verify embedding service is called on page create/update and fails gracefully when gateway is down
8. App 05 tests verify 409 Conflict when trying to approve an already-approved document
9. App 06 AI generate plan test verifies real tasks from LLM JSON are returned (not hardcoded 3 stubs)
10. App 10 tests verify cross-tenant KR update returns 404

## Tech Stack
- Java 21, Spring Boot 3.3.5
- `@SpringBootTest + @AutoConfigureMockMvc`
- `spring-security-test`: `SecurityMockMvcRequestPostProcessors.jwt()`
- `@MockBean JwtDecoder` on every class (blocks Keycloak)
- `@MockBean LiteLlmApi` on classes that test AI endpoints (blocks LiteLLM gateway)
- `JdbcTemplate` for DB seeding
- Real PostgreSQL at `localhost:5433` — NO Testcontainers
- JUnit 5, AssertJ, MockMvc, Mockito
