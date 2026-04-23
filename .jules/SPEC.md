# SPEC - WO-TEST-03: Integration Tests

## Goal
Implement integration tests for three applications:
- App 02: Team Feedback & Roadmap
- App 04: Invoice Payment Tracker
- App 08: API Key Management Portal

The tests must follow a specific pattern using `@SpringBootTest`, `@AutoConfigureMockMvc`, and manual DB seeding via `JdbcTemplate` on a real PostgreSQL instance (localhost:5433).

## Requirements

### Global
- Use `@ActiveProfiles("test")`.
- Disable Flyway in tests (`spring.flyway.enabled=false`).
- Use `@MockBean JwtDecoder jwtDecoder` to bypass Keycloak.
- Use `SecurityMockMvcRequestPostProcessors.jwt()` for authenticated requests.
- Seed `cc.tenants` before any app-specific data.
- Cleanup seeded data in `@AfterEach`.
- Use real PostgreSQL at `localhost:5433`.

### App 02 — Team Feedback & Roadmap
- `BoardControllerTest.java`: List, Create, Get, Tenant Isolation, Validation.
- `PostControllerTest.java`: List, Create, Vote, Update Status.
- `PublicFeedbackControllerTest.java`: Unauthenticated access to public boards and post submission.

### App 04 — Invoice Payment Tracker
- `InvoiceControllerTest.java`: List, Create, Send, Mark Paid, Public Access via Token, Tenant Isolation, Validation.

### App 08 — API Key Management Portal
- `ApiKeyControllerTest.java`: List, Create (verify bcrypt hashing), Revoke, Tenant Isolation, Validation (X-API-KEY), Rotate.

## Success Criteria
- All tests pass.
- No Testcontainers used.
- Correct use of JWT claims for tenant isolation.
- Databases tables are cleaned up after each test.
