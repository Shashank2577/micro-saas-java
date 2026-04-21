# SaaS OS — Jules Execution Plan

## Goal
Fix all business logic stubs/bugs and add comprehensive test coverage across the 10-app monorepo. All work is delegated to Jules via the work orders in `.omc/work-orders/`.

## Repo
`shashank-saxena/micro-saas-applications-java` (check with `git remote get-url origin`)  
Base branch: `main`

---

## Phase 1 — Business Logic Fixes

Fire these at Jules. WO-002 must go first (creates exception classes that WO-001 depends on). WO-003, WO-004, WO-005 can fire in parallel after WO-002 is merged.

| Order | WO | File | What it does | Depends on |
|-------|----|------|-------------|------------|
| 1 | WO-002 | `WO-002-fix-error-handling.md` | Create `EntityNotFoundException` + `ForbiddenException` in saas-os-core; fix all `orElseThrow(RuntimeException)` in apps 02, 04, 05, 06, 07, 10 | — |
| 2a | WO-001 | `WO-001-fix-tenant-isolation.md` | Replace header-based and fallback-UUID tenant extraction with `TenantResolver` in apps 01, 03, 06, 10 | WO-002 merged |
| 2b | WO-003 | `WO-003-fix-stripe-webhook.md` | Fix zero-UUID extraction, epoch timestamp parse crash, re-enable Stripe signature verification | WO-002 merged |
| 2c | WO-004 | `WO-004-implement-ai-knowledge-base.md` | Real RAG in App 03: EmbeddingService, vector search, live Q&A replacing all stubs | WO-002 merged |
| 2d | WO-005 | `WO-005-fix-app06-ai-onboarding.md` | Real LLM-generated onboarding tasks in App 06 (replaces 3 hardcoded stubs) | WO-002 merged |

**After all Phase 1 PRs are merged**, run:
```bash
mvn compile -pl saas-os-core,apps/01-client-portal-builder,apps/02-team-feedback-roadmap,apps/03-ai-knowledge-base,apps/04-invoice-payment-tracker,apps/05-document-approval-workflow,apps/06-employee-onboarding-orchestrator,apps/07-lightweight-issue-tracker,apps/10-okr-goal-tracker
```

---

## Phase 2 — Test Coverage

Fire these after Phase 1 is merged. All 4 test WOs can run in parallel.

| WO | File | What it covers |
|----|------|---------------|
| WO-TEST-01 | `WO-TEST-01-saas-os-core-unit-tests.md` | saas-os-core unit tests: AiService, TenantResolver, GlobalExceptionHandler, LocalTenantResolver |
| WO-TEST-02 | `WO-TEST-02-integration-tests-app09-and-app07.md` | App 09 Changelog (posts, AI, public changelog) + App 07 Issue Tracker (issues, projects, AI) |
| WO-TEST-03 | `WO-TEST-03-integration-tests-app02-app04-app08.md` | App 02 Feedback Roadmap + App 04 Invoice Tracker + App 08 API Key Portal |
| WO-TEST-04 | `WO-TEST-04-integration-tests-app01-app03-app05-app06-app10.md` | App 01 Portal Builder + App 03 AI KB + App 05 Approvals + App 06 Onboarding + App 10 OKR |

---

## Key Constraints Jules Must Know (include in every prompt)

1. **Java 21, Spring Boot 3.3.5** — do not upgrade anything
2. **No new dependencies** — use only what is already in each app's pom.xml
3. **saas-os-core rebuild** — after any change to saas-os-core, run `mvn install -pl saas-os-core` before building dependent apps
4. **No Testcontainers** — tests connect to real PostgreSQL at `localhost:5433`, database `changelog`
5. **Flyway disabled in tests** — `spring.flyway.enabled=false` in test properties
6. **`cc.tenants` FK** — all app tables have FK to `cc.tenants(id)`; seed that row first in every test
7. **JWT mock** — `@MockBean JwtDecoder jwtDecoder` on every test class to bypass Keycloak
8. **LiteLlmApi mock** — `@MockBean LiteLlmApi` on test classes that touch AI endpoints

---

## Infrastructure (already running locally)

| Service | URL / Port | Notes |
|---------|-----------|-------|
| PostgreSQL + pgvector | `localhost:5433` | Docker: `pgvector/pgvector:pg16` image, named volume `changelog-platform_postgres_data` |
| Keycloak | `localhost:8080` | Realm: `changelog` |
| MinIO | `localhost:9000` | Credentials: minioadmin/minioadmin |
| LiteLLM gateway | `localhost:4000` | OpenAI-compatible, needed for AI features |

Start with: `docker-compose up -d` from repo root

---

## App Port Map

| App | Port |
|-----|------|
| 01 Client Portal Builder | 8081 |
| 02 Feedback Roadmap | 8082 |
| 03 AI Knowledge Base | 8083 |
| 04 Invoice Tracker | 8084 |
| 05 Document Approval | 8085 |
| 06 Employee Onboarding | 8086 |
| 07 Issue Tracker | 8087 |
| 08 API Key Portal | 8088 |
| 09 Changelog Platform | 8080 (or 8089) |
| 10 OKR Tracker | 8090 |

---

## Session Tracker

Update this table after each Jules session.

| WO | Session ID | Session URL | PR | Status |
|----|-----------|------------|-----|--------|
| WO-002 | 13862609603741560147 | https://jules.google.com/session/13862609603741560147 | — | IN_PROGRESS |
| WO-001 | — | — | — | NOT STARTED |
| WO-003 | — | — | — | NOT STARTED |
| WO-004 | — | — | — | NOT STARTED |
| WO-005 | — | — | — | NOT STARTED |
| WO-TEST-01 | — | — | — | NOT STARTED |
| WO-TEST-02 | — | — | — | NOT STARTED |
| WO-TEST-03 | — | — | — | NOT STARTED |
| WO-TEST-04 | — | — | — | NOT STARTED |
