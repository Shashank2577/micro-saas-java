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

## Important Notes
- The `local` Spring profile bypasses Keycloak for easier dev/testing.
- JWT tokens include a `tenant_id` claim which MUST be used for data isolation.
- Schema migrations are handled by Flyway in `src/main/resources/db/migration`.
