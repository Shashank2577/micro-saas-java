# AGENTS.md

## Project Overview
A multi-tenant SaaS Operating System and Changelog Platform. It's built to support multiple applications (e.g., Changelog, CRM, etc.) under a single platform infrastructure, sharing identity and tenant management.

## Tech Stack
- **Backend**: Java 25 (Spring Boot 3.3.5)
- **Database**: PostgreSQL 16
- **Auth**: Keycloak (OIDC)
- **Infrastructure**: Docker Compose, MinIO
- **Testing**: JUnit 5, MockMvc, WireMock (planned)
- **Build Tool**: Maven

## Code Conventions
- **Naming**: camelCase for Java, snake_case for database.
- **Structure**: Domain-driven organization (e.g., `com.changelog.business.acquisition`).
- **Security**: Tenant isolation enforced at the service/controller level via JWT claims.
- **API**: RESTful, following RFC 7807 for error details.

## Build & Test
- Build: `./mvnw clean install` (or `mvn` if wrapper is missing)
- Run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- Test: `mvn test`

## Monorepo Structure
- `saas-os-core/` — shared library (exceptions, DTOs, config). Always rebuild with `mvn install -pl saas-os-core` after any change here before building dependent apps.
- `apps/01-client-portal-builder/` through `apps/10-okr-goal-tracker/` — individual Spring Boot apps
- Each app has its own Flyway migrations in `src/main/resources/db/migration/`

## Dependency Rules
- **DO NOT add new Maven dependencies.** Use only what is already in each app's `pom.xml`.
- **Java 25, Spring Boot 3.3.5** — do not upgrade or downgrade anything.
- Lombok version must be `1.18.44` or higher in `saas-os-core/pom.xml`.

## Database
- PostgreSQL 16 with pgvector extension running at `localhost:5433` (host port), database: `changelog`
- **Do NOT use Testcontainers** — the Docker client is incompatible with Docker Engine 29.x on this machine.
- Tests connect to the real PostgreSQL at `localhost:5433`.

## Testing Rules
- `spring.flyway.enabled=false` in test application properties (migrations already applied).
- All test classes must have `@MockBean JwtDecoder jwtDecoder` to bypass Keycloak.
- Test classes touching AI endpoints must have `@MockBean LiteLlmApi liteLlmApi`.
- Seed `cc.tenants` table row before any test that inserts app data (all app tables have FK to `cc.tenants(id)`).

## Tenant Isolation
- Tenant ID comes from the JWT claim `tenant_id` — always extract via `TenantResolver` in `saas-os-core`.
- Never use request headers or hardcoded UUIDs for tenant extraction in production code.

## Infrastructure (local dev)
| Service | URL | Credentials |
|---------|-----|-------------|
| PostgreSQL + pgvector | localhost:5433 | see docker-compose.yml |
| Keycloak | localhost:8080 | Realm: changelog |
| MinIO | localhost:9000 | minioadmin / minioadmin |
| LiteLLM gateway | localhost:4000 | OpenAI-compatible |

Start with: `docker-compose up -d` from repo root.

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
| 09 Changelog Platform | 8089 |
| 10 OKR Tracker | 8090 |

## Important Notes
- The `local` Spring profile bypasses Keycloak for easier dev/testing.
- JWT tokens include a `tenant_id` claim which MUST be used for data isolation.
- Schema migrations are handled by Flyway in `src/main/resources/db/migration`.
- Exception hierarchy: all custom domain exceptions extend `RuntimeException` (unchecked).
- `GlobalExceptionHandler` is annotated with `@ControllerAdvice` in `saas-os-core/src/main/java/com/changelog/config/GlobalExceptionHandler.java`.
