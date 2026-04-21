# Handoff - Lightweight Issue Tracker

## Project Overview
The Lightweight Issue Tracker is a new module implemented under `apps/07-lightweight-issue-tracker`. It provides a simple yet powerful issue tracking system for small teams, supporting multi-tenancy and AI-powered features.

## Architecture
- **Backend**: Spring Boot 3.3.5, Java 21.
- **Database**: PostgreSQL 16 with `pgvector` extension for embeddings and `tsvector` for full-text search.
- **Multi-tenancy**: Data isolation is enforced at the controller level by extracting the `tenant_id` from the JWT token using `TenantResolver`.
- **AI Integration**: Leverages the centralized `AiService` in `saas-os-core` for duplicate issue detection and priority suggestion.

## Implementation Details
- **Entities**: Projects, Issues, Labels, Comments, Attachments, IssueLinks, and IssueEvents.
- **Repositories**: Standard Spring Data JPA repositories with tenant-aware queries.
- **Services**: Business logic for issue lifecycle management, event logging, and AI features.
- **Controllers**: RESTful endpoints for all specified operations.
- **Migrations**: Flyway migration in `V1__issues.sql` sets up the schema and enables the `vector` extension.

## Configuration
- `application.yml` is configured for two profiles:
    - **Default**: Connects to a PostgreSQL database.
    - **local**: Uses an H2 in-memory database with PostgreSQL compatibility mode for development and testing.

## How to Run
### Local Testing
Run tests using:
```bash
mvn test -pl apps/07-lightweight-issue-tracker -am
```

### Running the Application
The application runs on port `8081` by default.
```bash
mvn spring-boot:run -pl apps/07-lightweight-issue-tracker -Dspring-boot.run.profiles=local
```

## AI Features
- **Duplicate Check**: `POST /api/issues/ai/check-duplicate` - Compares a new issue title/description against existing issues.
- **Priority Suggestion**: `POST /api/issues/{issueId}/ai/suggest-priority` - Suggests a priority based on the issue content.

## Notable Decisions
- Used `float[]` in Java to map to PostgreSQL's `vector` type for embeddings.
- Implemented `AuditingConfig` to provide `OffsetDateTime` for `@CreatedDate` and `@LastModifiedDate`.
- Registered `Hibernate6Module` in `JacksonConfig` to handle lazy loading of entities during JSON serialization.
