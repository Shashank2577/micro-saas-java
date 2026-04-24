# Feature List

## App 09: Changelog & Release Notes Platform (Core SaaS OS)
- **Projects**: Full CRUD, custom slugs, JSONB branding configurations.
- **Posts**: Draft, Scheduled, and Published lifecycles with Markdown support.
- **Tags**: Categorize updates (e.g., New Feature, Bug Fix).
- **Public Changelog**: Unauthenticated, paginated read-only view.
- **In-App Widget**: Configurable embedded widget for customer apps.
- **Subscriber Management**: Opt-in/out logic for notifications.
- **Multi-Tenancy**: Data isolated by `tenant_id` at the database level.
- **Authentication**: Keycloak OAuth2 resource server integration.

## App 02: Team Feedback & Roadmap
- **Feedback Submission**: Allow users to submit ideas and bugs.
- **Voting System**: Upvote/downvote capabilities for feature requests.
- **Roadmap Generation**: Map ideas to Planned, In-Progress, and Done statuses.

## Shared Infrastructure (All 10 Apps)
- **Shared DB**: PostgreSQL 16 with Flyway Migrations.
- **Identity & Access Management**: Keycloak 24.
- **Object Storage**: S3-compatible MinIO for file storage.
- **Mailing**: Spring Mail.
- **Payments**: Stripe processing.
