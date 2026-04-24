# Internal Project State: Micro-SaaS Applications

## Current High-Level Status

We are building a portfolio of 10 micro-SaaS applications using a shared "SaaS Operating System". The core premise is that a single infrastructure foundation (App 09) will power all 10 independent applications.

**Overall Progress:** ~15% Complete.
- **App 09 (Changelog & Release Notes Platform):** Actively in progress. The core SaaS OS (backend) is mostly complete. The frontend and some specific features are pending.
- **Apps 01-08, 10:** Planned. No core domain logic has been implemented yet. App 02 is the designated "next up".

## Shared Infrastructure (SaaS OS) Status

The SaaS Operating System is housed within the `saas-os-core` and the `09-changelog-platform` codebase.

| Component | Technology | Status | Notes |
|-----------|-----------|--------|-------|
| **Database** | PostgreSQL 16 | ✅ Configured | Multi-tenant schema via Flyway (V1-V4). `tenant_id` scoping active. |
| **Authentication** | Keycloak 24 | ✅ Configured | OAuth2/OIDC running locally via Docker Compose. `TenantResolver` is active. |
| **Object Storage**| MinIO | ✅ Configured | S3-compatible storage is up, but upload endpoints (App 09) are pending. |
| **Payments** | Stripe | 🚧 In Progress | Product catalog modelled, webhooks built. Checkout session flow pending. |
| **Email/Retention**| Spring Mail | 🚧 In Progress | Drip engine and templates exist. SMTP sending logic is logged, not wired to real ESP. |

## Application 09 (Changelog Platform) Status

This is the anchor application. 

### What's Built (Backend Core)
- **Projects:** Full CRUD operations, custom slugs, and JSONB branding configurations.
- **Posts:** Lifecycle management (Draft, Scheduled, Published), Markdown content support, full-text search column (`tsvector`).
- **Tags:** Project-specific categorization.
- **Public API:** `GET /changelog/{slug}` - unauthenticated, paginated, published-only reads.

### Pending Implementation (App 09)
1. **Frontend UI:** Currently missing the React/Thymeleaf admin dashboard and the public-facing pages.
2. **JavaScript Embed:** Need to build the CDN-hosted `<script>` for the "What's New" widget.
3. **Email Delivery:** Wire up the actual subscriber notifications on post publish.
4. **Stripe Checkout:** Wire the frontend/backend flow to initiate a Stripe Checkout Session.
5. **File Uploads:** Expose endpoints for MinIO.
6. **AI Post Generation:** LiteLLM gateway is configured but the integration for generating posts is pending.
7. **RSS Feed:** `GET /changelog/{slug}/feed.xml` needs building.

## Next Steps

1. **Complete App 09 Frontend:** The immediate priority is closing out the frontend implementation for App 09 to prove out the full SaaS OS stack (Auth -> API -> UI -> DB).
2. **App 02 Implementation (Team Feedback):** Once App 09 is functionally complete, we will begin scaffolding App 02. The goal here is to prove that the SaaS OS can be imported and reused without modifying its core, only adding the domain-specific logic for voting and roadmapping.
3. **Continuous Integration:** Ensure that as apps are added, the shared `saas-os-core` module tests continue to pass and backward compatibility is maintained.
