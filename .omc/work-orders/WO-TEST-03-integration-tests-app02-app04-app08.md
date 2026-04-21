# WO-TEST-03: Integration Tests — App 02 (Feedback Roadmap) + App 04 (Invoice Tracker) + App 08 (API Key Portal)

## Context
Three apps covering distinct business domains: public feedback collection (App 02), B2B invoicing (App 04), and API key management (App 08). None currently have tests.

The test pattern to follow is in `apps/09-changelog-platform/src/test/java/com/changelog/api/ProjectControllerTest.java`. Key patterns:
- `@SpringBootTest + @AutoConfigureMockMvc` on the class
- `@MockBean private JwtDecoder jwtDecoder` to prevent Keycloak calls
- JWT injection via `.with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject(userId.toString())))`
- `@Autowired JdbcTemplate jdbcTemplate` for DB seeding in `@BeforeEach`
- `cc.tenants` must be inserted before app-specific data (FK constraint)
- `@AfterEach` cleans up rows inserted by the test

**Database**: Real PostgreSQL at `localhost:5433`, database `changelog`. Flyway is DISABLED in tests. Tables must already exist from app startup.

---

## App 02 — Team Feedback & Roadmap

### Test dependencies
Read `apps/02-team-feedback-roadmap/pom.xml`. Add if missing:
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
Create `apps/02-team-feedback-roadmap/src/test/resources/application-test.properties`:
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

### File 1: `BoardControllerTest.java`

Path: `apps/02-team-feedback-roadmap/src/test/java/com/changelog/BoardControllerTest.java`

Read `apps/02-team-feedback-roadmap/src/main/java/com/changelog/model/Board.java` and
`apps/02-team-feedback-roadmap/src/main/resources/db/migration/V1__*.sql` to understand table and column names.

**Setup `@BeforeEach`:**
- Insert `cc.tenants(id, name, slug, plan_tier)`
- Insert one `boards(id, tenant_id, name, slug, status)` row

**Tests:**

`testListBoards_returnsCurrentTenantBoards()`
- GET `/api/v1/boards` with JWT for `tenantId`
- Assert status 200
- Assert JSON array has 1 element matching the seeded board

`testListBoards_excludesOtherTenantBoards()`
- Seed a board for a second `tenantB` UUID
- GET `/api/v1/boards` with JWT for `tenantId`
- Assert the `tenantB` board is NOT in the response

`testCreateBoard_persistsBoard()`
- POST `/api/v1/boards` with JWT
- Body: `{"name": "Feature Requests", "slug": "feature-requests"}` (check actual required fields from `CreateBoardRequest.java`)
- Assert status 201 or 200
- Assert `$.tenantId` = `tenantId.toString()`

`testGetBoard_returns404ForUnknownId()`
- GET `/api/v1/boards/{UUID.randomUUID()}` with JWT
- Assert status 404

`testCreateBoard_returns400WhenNameMissing()`
- POST `/api/v1/boards` with JWT and body `{}`
- Assert status 400

### File 2: `PostControllerTest.java`

Path: `apps/02-team-feedback-roadmap/src/test/java/com/changelog/PostControllerTest.java`

Read `apps/02-team-feedback-roadmap/src/main/java/com/changelog/model/FeedbackPost.java` and the controller at
`apps/02-team-feedback-roadmap/src/main/java/com/changelog/controller/PostController.java`.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert `boards` row (FK dependency)
- Insert one `feedback_posts(id, tenant_id, board_id, title, status, vote_count)` row

**Tests:**

`testListPosts_returnsBoardPosts()`
- GET `/api/v1/boards/{boardId}/posts` with JWT
- Assert status 200
- Assert contains the seeded post

`testCreatePost_increasesPostCount()`
- POST `/api/v1/boards/{boardId}/posts` with JWT
- Body: `{"title": "Add dark mode", "description": "Would love dark mode"}` (check `CreatePostRequest.java`)
- Assert status 201 or 200
- Assert `$.title` = `"Add dark mode"`

`testVotePost_incrementsVoteCount()`
- POST `/api/v1/posts/{postId}/vote` with JWT (check actual vote endpoint in PostController)
- Assert status 200
- GET the post again
- Assert `$.voteCount` (or however it's named) is 1 higher

`testUpdatePostStatus_changesStatusToUnderReview()`
- PATCH or PUT the post status endpoint with JWT (find it in PostController)
- Body: `{"status": "under_review"}`
- Assert status 200
- Assert `$.status` = `"under_review"`

### File 3: `PublicFeedbackControllerTest.java`

Path: `apps/02-team-feedback-roadmap/src/test/java/com/changelog/PublicFeedbackControllerTest.java`

Read `apps/02-team-feedback-roadmap/src/main/java/com/changelog/controller/PublicFeedbackController.java` — this is the unauthenticated public endpoint.

**Tests:**

`testPublicBoardPosts_returnsOpenPostsWithoutAuth()`
- Seed a board and two posts: one `"open"`, one `"closed"`
- GET the public board URL (check PublicFeedbackController for the mapping, likely `/public/boards/{slug}/posts`)
- WITHOUT JWT
- Assert status 200
- Assert open post is in response
- (Check whether closed posts are excluded based on controller logic)

`testPublicSubmitPost_createsPostWithoutAuth()`
- POST to the public submission endpoint (find it in PublicFeedbackController)
- Body: `{"title": "Public suggestion", "email": "user@example.com"}`
- Assert status 201 or 200
- Query DB to verify post was inserted

---

## App 04 — Invoice Payment Tracker

### Test dependencies
Read `apps/04-invoice-payment-tracker/pom.xml`. Add if missing:
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
Create `apps/04-invoice-payment-tracker/src/test/resources/application-test.properties`:
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

### File 4: `InvoiceControllerTest.java`

Path: `apps/04-invoice-payment-tracker/src/test/java/com/changelog/invoice/InvoiceControllerTest.java`

Read the migration SQL at `apps/04-invoice-payment-tracker/src/main/resources/db/migration/V1__invoicing.sql` for table/column names. Read the `Invoice` model and `InvoiceController`.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert `clients(id, tenant_id, name, email, currency)` row (find actual columns from migration)
- Insert `invoices(id, tenant_id, client_id, invoice_number, status, total_amount, due_date)` row with status `"draft"`

**Tests:**

`testListInvoices_returnsCurrentTenantInvoices()`
- GET `/api/v1/invoices` with JWT for `tenantId`
- Assert status 200
- Assert contains the seeded invoice

`testCreateInvoice_persistsCorrectly()`
- POST `/api/v1/invoices` with JWT
- Body: `{"clientId": "{clientId}", "dueDate": "2026-12-31", "lineItems": [{"description": "Consulting", "quantity": 10, "unitPrice": 150.00}]}`
  (Adjust based on actual `CreateInvoiceRequest` DTO fields)
- Assert status 201 or 200
- Assert `$.tenantId` = `tenantId.toString()`
- Assert `$.status` = `"draft"`

`testSendInvoice_changesStatusToSent()`
- POST `/api/v1/invoices/{invoiceId}/send` with JWT (find actual endpoint)
- Assert status 200
- Assert `$.status` = `"sent"` (or equivalent)

`testMarkInvoicePaid_changesStatusToPaid()`
- POST `/api/v1/invoices/{invoiceId}/mark-paid` with JWT
- Assert status 200
- Assert `$.status` = `"paid"`

`testGetInvoiceByPublicToken_returnsInvoiceWithoutAuth()`
- Find the public invoice endpoint (check `InvoiceController` for a `?token=` parameter endpoint)
- If it exists: GET the invoice using the `public_token` (or `payment_token`) column value from the seeded row
- WITHOUT JWT
- Assert status 200
- Assert invoice title/amount matches seeded data

`testGetInvoice_returns404ForOtherTenantInvoice()`
- GET `{tenantA_invoiceId}` with JWT for `tenantB`
- Assert status 404

`testCreateInvoice_returns400WhenClientIdMissing()`
- POST `/api/v1/invoices` with incomplete body
- Assert status 400

---

## App 08 — API Key Management Portal

### Test dependencies
Read `apps/08-api-key-management-portal/pom.xml`. Add if missing (spring-security-test was added in a previous fix — verify it's there):
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
Create `apps/08-api-key-management-portal/src/test/resources/application-test.properties`:
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

### File 5: `ApiKeyControllerTest.java`

Path: `apps/08-api-key-management-portal/src/test/java/com/changelog/apikey/ApiKeyControllerTest.java`

Read the migration SQL at `apps/08-api-key-management-portal/src/main/resources/db/migration/V1__api_keys.sql` for table/column names. Read the `ApiKey` and `ApiConsumer` models, and the controller(s) in the package.

**Setup `@BeforeEach`:**
- Insert `cc.tenants` row
- Insert `api_consumers(id, tenant_id, external_id, name)` row
- Insert `api_keys(id, tenant_id, consumer_id, name, key_prefix, key_hash, status)` row with `status = 'active'`
  - Use a bcrypt hash placeholder for `key_hash` — you can hardcode `$2a$10$AAAAAAAAAAAAAAAAAAAAAA` as a dummy hash for test data

**Tests:**

`testListApiKeys_returnsActiveKeys()`
- GET `/api/v1/api-keys` or `/api/v1/consumers/{consumerId}/keys` (check actual endpoint mapping in the controller)
- With JWT for `tenantId`
- Assert status 200
- Assert response contains the seeded key by `name` or `keyPrefix`

`testCreateApiKey_returnsPlaintextKeyOnce()`
- POST the key creation endpoint with JWT
- Body: `{"consumerId": "{consumerId}", "name": "Production Key", "scopes": ["read:data"], "environment": "production"}`
  (Adjust to actual `CreateApiKeyRequest` fields)
- Assert status 201 or 200
- Assert response contains the full plaintext key (only returned at creation — should start with a prefix like `sk_live_`)
- Assert `$.keyPrefix` is non-null and is the first 8 chars of the returned key
- Query DB to verify `key_hash` is NOT the plaintext key (it should be a bcrypt hash)

`testRevokeApiKey_changesStatusToRevoked()`
- POST `/api/v1/api-keys/{keyId}/revoke` with JWT (find actual endpoint)
- Assert status 200
- Assert `$.status` = `"revoked"`
- Query DB to verify `status` and `revoked_at` columns

`testRevokeKey_returns404ForOtherTenantKey()`
- Seed a key for `tenantA`
- POST revoke with JWT for `tenantB`
- Assert status 404

`testValidateApiKey_returnsValidForActiveKey()`
- Find the key validation endpoint (internal or public) in the controller
- If it exists: call it with the correct key value
- Assert status 200 with `valid: true`

`testRotateKey_createsNewKeyAndRevokesOld()`
- POST `/api/v1/api-keys/{keyId}/rotate` with JWT (find actual rotate endpoint)
- Assert status 201 or 200
- Assert new key has `rotation_of = {oldKeyId}`
- Assert old key has `status = "revoked"` (query DB)

`testCreateApiKey_returns400WhenConsumerIdMissing()`
- POST with body missing `consumerId`
- Assert status 400

## Acceptance Criteria
1. All 7 test files compile without errors
2. All tests pass when PostgreSQL is running: `mvn test -pl apps/02-team-feedback-roadmap,apps/04-invoice-payment-tracker,apps/08-api-key-management-portal` reports 0 failures
3. `@MockBean private JwtDecoder jwtDecoder` present in every authenticated test class
4. `@ActiveProfiles("test")` on every test class
5. `@AfterEach` cleans up all inserted rows so tests are independent
6. No Testcontainers usage anywhere
7. App 08 key creation test verifies the key is bcrypt-hashed in the database (plaintext never stored)
8. App 02 public endpoint tests do NOT attach JWT (verify truly unauthenticated access works)

## Tech Stack
- Java 21, Spring Boot 3.3.5
- `@SpringBootTest + @AutoConfigureMockMvc`
- `spring-security-test`: `SecurityMockMvcRequestPostProcessors.jwt()`
- `@MockBean JwtDecoder jwtDecoder` on every class
- `JdbcTemplate` for direct DB seeding (not JPA repositories — avoids complex entity graph setup)
- Real PostgreSQL at `localhost:5433` — NO Testcontainers
- AssertJ + MockMvc matchers
