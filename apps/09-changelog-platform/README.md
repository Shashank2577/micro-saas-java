# Changelog & Release Notes Platform

> Publish product updates professionally and automatically notify everyone who cares.

App 09 of the [micro-saas-applications-java](../README.md) repository. A Beamer/Headway alternative built with Spring Boot 3.3.5.

---

## What It Does

SaaS companies ship features constantly but communicate them poorly. Customers miss new features, form an impression the product is stagnant, and churn — even when the team ships weekly.

This platform solves that by giving companies:
- A **public changelog page** (`yourproduct.io/changelog`) — SEO-indexable, shareable, branded
- An **embeddable in-app widget** — the "What's New" bell icon inside the product
- **Automatic subscriber emails** — every published post reaches everyone who opted in
- A **full SaaS operating system** built on top — from landing page A/B tests to customer health scoring

---

## Current State

### Done

| Area | What's built |
|------|-------------|
| **Core changelog** | Projects, posts (draft/scheduled/published), tags, full-text search column, scheduled publishing field |
| **Public changelog** | `GET /changelog/{slug}` — unauthenticated, paginated, published posts only |
| **Widget config** | Position, colours, allowed origins stored per project |
| **Subscriber management** | Opt-in/opt-out, segmentation by plan tier |
| **Acquisition module** | Landing pages with A/B variant testing, conversion tracking |
| **Monetisation module** | Stripe products, subscriptions, webhook handler (created/updated/deleted/payment events) |
| **Retention module** | Drip campaign engine — templates, enrollments, hourly step processor, wired into Stripe events |
| **Success module** | Customer health scoring (0–100 + risk signals), support tickets (AI-classified, sentiment scoring) |
| **Intelligence module** | Analytics event tracking, funnel analysis, unit economics (MRR/ARR/CAC/LTV) |
| **Multi-tenancy** | Every row scoped to `tenant_id`, Keycloak JWT extracts tenant context |
| **Local dev** | Docker Compose (postgres:5433, keycloak, minio), `local` Spring profile (no Keycloak required) |
| **Database** | Flyway V1–V4 migrations, 20+ tables, JSONB for flexible metadata |
| **Auth** | Keycloak OAuth2 resource server; `TenantResolver` pattern for local vs. production |

### In Progress / Not Yet Started

| Area | Status | Notes |
|------|--------|-------|
| JavaScript widget | Not started | CDN-hosted `<script>` embed, "What's New" bell icon |
| Email delivery | Not started | Subscriber notifications on post publish; drip sends (currently logged only) |
| RSS feed | Not started | `GET /changelog/{slug}/feed.xml` |
| Frontend UI | Not started | React or Thymeleaf — public changelog page and admin dashboard |
| Custom domain | Not started | CNAME to platform, SSL via Let's Encrypt |
| AI post generation | Not started | LiteLLM gateway configured in `application.yml`, integration pending |
| Scheduled publishing | Partially done | `scheduledFor` field exists on Post; background job not yet wired |
| MinIO file uploads | Not started | Config present in `application.yml`; upload endpoint missing |
| Stripe checkout | Not started | Products and subscriptions modelled; checkout session flow not built |
| Full-text search endpoint | Not started | PostgreSQL `tsvector` column exists; search API not exposed |

---

## Architecture

```
changelog-platform/
├── src/main/java/com/changelog/
│   ├── config/                       # Security, CORS, tenant resolution
│   │   ├── SecurityConfig.java       # Keycloak OAuth2 (non-local profile)
│   │   ├── SecurityConfigLocal.java  # Permit-all for local dev
│   │   ├── TenantResolver.java       # Interface: getTenantId(jwt), getUserId(jwt)
│   │   ├── JwtTenantResolver.java    # Production: extracts from JWT claims
│   │   └── LocalTenantResolver.java  # Local dev: fixed seed tenant UUID
│   │
│   ├── controller/
│   │   ├── ApiV1Controller.java           # Auth'd: /api/v1/projects, /api/v1/posts
│   │   └── PublicChangelogController.java # Public: /changelog/{slug}
│   │
│   ├── model/                        # Core JPA entities
│   │   ├── Project.java              # Changelog project (slug, branding JSONB)
│   │   ├── Post.java                 # Release note (DRAFT/SCHEDULED/PUBLISHED)
│   │   ├── Tag.java                  # Category tag (New Feature, Bug Fix, etc.)
│   │   ├── Subscriber.java           # Email subscriber with opt-out
│   │   └── WidgetConfig.java         # In-app widget settings
│   │
│   ├── service/                      # PostService, ProjectService
│   ├── repository/                   # Spring Data JPA
│   ├── dto/                          # Request/response objects
│   │
│   └── business/                     # SaaS Operating System modules
│       ├── acquisition/              # Landing pages + A/B testing
│       ├── monetization/             # Stripe payments + webhooks
│       ├── retention/                # Drip campaign automation
│       ├── success/                  # Health scoring + support tickets
│       ├── intelligence/             # Analytics + funnel + unit economics
│       └── orchestration/            # Domain events (BusinessEvent, publisher)
│
└── src/main/resources/
    ├── application.yml               # Production config
    ├── application-local.yml         # Local dev overrides (port 8081, postgres 5433)
    └── db/migration/
        ├── V1__init.sql              # Core schema (tenants, projects, posts, tags, subscribers, widgets)
        ├── V2__sample_data.sql       # Demo data (tenant, users, project + posts)
        ├── V3__business_modules.sql  # SaaS OS tables (landing pages, stripe, analytics, success)
        └── V4__drip_campaigns.sql    # Drip campaign tables
```

---

## Database Schema

### Core Tables (V1)

| Table | Key columns |
|-------|------------|
| `cc.tenants` | `id`, `name`, `slug`, `plan_tier` (free/startup/growth/scale) |
| `cc.users` | `id`, `tenant_id`, `email`, `role` (admin/editor/publisher) |
| `changelog_projects` | `id`, `tenant_id`, `name`, `slug`, `branding` (JSONB), `custom_domain` |
| `changelog_tags` | `id`, `tenant_id`, `name`, `color`, `slug` |
| `changelog_posts` | `id`, `project_id`, `title`, `content` (markdown), `status`, `published_at`, `scheduled_for`, `tsv_content` |
| `changelog_subscribers` | `id`, `project_id`, `email`, `plan_tier`, `opted_out_at` |
| `widget_configs` | `id`, `project_id`, `position`, `primary_color`, `allowed_origins` |
| `cc.audit_events` | `id`, `tenant_id`, `actor_id`, `action`, `resource_type`, `payload` |

### SaaS OS Tables (V3)

| Table | Purpose |
|-------|---------|
| `landing_pages` / `landing_variants` | A/B test landing pages with conversion tracking |
| `stripe_products` / `stripe_subscriptions` | Pricing plans and active subscriptions |
| `analytics_events` | Business + technical event stream (JSONB properties) |
| `funnel_analytics` | Conversion funnel snapshots (steps + rates as JSONB) |
| `unit_economics` | Monthly MRR, ARR, CAC, LTV, LTV:CAC ratio, churn rate |
| `customer_health_scores` | Per-customer score 0–100, risk level, signals (JSONB) |
| `support_tickets` | Tickets with AI category classification and sentiment score |

### Retention Tables (V4)

| Table | Purpose |
|-------|---------|
| `drip_campaigns` | Campaign template (trigger_event, steps as JSONB) |
| `drip_enrollments` | Per-customer enrollment (current_step, next_step_at) |

---

## API Endpoints

### Public (no auth)

```
GET  /changelog/{slug}                  Project info + all published posts
GET  /changelog/{slug}/posts            Paginated posts (?page=0&size=20)
GET  /changelog/{slug}/posts/{postId}   Single published post
```

### Authenticated

```
# Projects
GET    /api/v1/projects
POST   /api/v1/projects
GET    /api/v1/projects/{id}
PUT    /api/v1/projects/{id}
DELETE /api/v1/projects/{id}

# Posts
GET    /api/v1/projects/{id}/posts
POST   /api/v1/projects/{id}/posts       Creates as DRAFT
GET    /api/v1/posts/{id}
PUT    /api/v1/posts/{id}
POST   /api/v1/posts/{id}/publish        → status = PUBLISHED
DELETE /api/v1/posts/{id}

# Analytics & Unit Economics
GET    /api/v1/analytics/dashboard
GET    /api/v1/analytics/unit-economics
GET    /api/v1/analytics/unit-economics/latest
GET    /api/v1/analytics/unit-economics/health-status
GET    /api/v1/analytics/funnels
POST   /api/v1/analytics/funnels/{name}/calculate

# Customer Success
GET    /api/v1/health-scores
GET    /api/v1/health-scores/at-risk
GET    /api/v1/health-scores/{customerId}
GET    /api/v1/health-scores/{customerId}/recalculate
GET    /api/v1/support-tickets
POST   /api/v1/support-tickets
GET    /api/v1/support-tickets/{id}
POST   /api/v1/support-tickets/{id}/status
POST   /api/v1/support-tickets/{id}/close

# Acquisition
GET    /api/v1/landing-pages
POST   /api/v1/landing-pages
GET    /api/v1/landing-pages/{id}
POST   /api/v1/landing-pages/{id}/activate
POST   /api/v1/landing-pages/{id}/variants
POST   /api/v1/landing-pages/public/{variantId}/view      (no auth)
POST   /api/v1/landing-pages/public/{variantId}/convert   (no auth)

# Stripe Webhooks
POST   /api/v1/stripe/webhooks
```

---

## Local Development

### Prerequisites

- Java 25 (or 21+)
- Maven 3.9+
- Docker + Docker Compose

### 1. Start infrastructure

```bash
docker compose up -d
```

Starts:
- PostgreSQL 16 on **port 5433** (avoids conflict with any local postgres on 5432)
- Keycloak 24 on **port 8080**
- MinIO on **ports 9000 / 9001**

### 2. Run the app

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile:
- Runs on **port 8081**
- Connects to postgres at `localhost:5433` (db: `changelog`, user/password: `changelog`)
- Skips Keycloak JWT validation — all endpoints accept unauthenticated requests
- Uses seeded demo tenant `550e8400-e29b-41d4-a716-446655440000` for all writes

### 3. Verify

```bash
# Public changelog (returns demo posts from V2 seed data)
curl http://localhost:8081/changelog/demo-project

# Health scores
curl http://localhost:8081/api/v1/health-scores

# Create a project
curl -X POST http://localhost:8081/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{"name":"My SaaS","slug":"my-saas","description":"Release notes"}'
```

### Full end-to-end flow (verified working)

```bash
# 1. Create project
curl -X POST http://localhost:8081/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{"name":"Acme SaaS","slug":"acme-saas","description":"Updates"}'

# 2. Create a post (note the project ID from step 1)
curl -X POST http://localhost:8081/api/v1/projects/{projectId}/posts \
  -H "Content-Type: application/json" \
  -d '{"title":"v1.0 - Initial Release","summary":"First release!","content":"# Whats new\n\n- Feature A"}'

# 3. Publish
curl -X POST http://localhost:8081/api/v1/posts/{postId}/publish

# 4. Read on public page
curl http://localhost:8081/changelog/acme-saas
# → {"projectName":"Acme SaaS","totalPosts":1,"posts":[...]}
```

---

## Configuration Reference

### Environment variables (production)

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/changelog
DATABASE_USERNAME=changelog
DATABASE_PASSWORD=changelog

KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/changelog

MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin

STRIPE_SECRET_KEY=sk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

---

## SaaS Operating System — Growth Engine

```
New visitor
    │
    ▼
[Acquisition]    Landing page A/B test → conversion tracked
    │
    ▼
[Monetisation]   Stripe checkout → subscription_created webhook fires
    │
    ▼
[Retention]      DripCampaignService.processEnrollmentTrigger("subscription_created")
                 → hourly scheduler sends drip steps
    │
    ▼
[Success]        Health score calculated → support tickets tracked
    │
    ▼
[Intelligence]   Funnel analysis → unit economics → real-time dashboard
```

### Drip Campaign Triggers

| Event | When |
|-------|------|
| `subscription_created` | Stripe webhook → welcome email series |
| `subscription_canceled` | Stripe webhook → win-back series |
| `trial_ending` | Scheduled job (not yet wired) |
| `churn_risk` | Health score drops to critical |

---

## Pricing Model

| Plan | Price | Projects | Widget branding | Custom domain | AI |
|------|-------|----------|----------------|--------------|-----|
| Free | $0 | 1 | Branded | — | — |
| Startup | $29/mo | 3 | Removed | — | — |
| Growth | $79/mo | 10 | Removed | ✓ | ✓ |
| Scale | $199/mo | Unlimited | Removed | ✓ | ✓ |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 3.3.5 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Auth | Keycloak 24 (OAuth2/OIDC) |
| Payments | Stripe |
| File storage | MinIO (S3-compatible) |
| Email | Spring Mail (SMTP) |
| JSON columns | Hibernate 6 + `@JdbcTypeCode(SqlTypes.JSON)` |
| Build | Maven |
| Boilerplate | Lombok |
| Local dev | Docker Compose |

---

## Roadmap

### Next sprint

1. **Email delivery** — wire Spring Mail to send subscriber notifications on post publish; replace `log.info("DRIP EMAIL:")` stub with real sends
2. **JavaScript widget** — CDN-hosted `<script>` that renders a "What's New" bell icon reading from `GET /changelog/{slug}/posts`
3. **Scheduled publishing** — background `@Scheduled` job to publish posts where `scheduledFor <= now()`

### Short term

4. **RSS feed** — `GET /changelog/{slug}/feed.xml`
5. **Admin frontend** — React or Thymeleaf dashboard for writing and publishing posts
6. **Stripe checkout** — pricing page → Stripe checkout session → subscription flow
7. **Custom domain** — CNAME support + automatic SSL via Let's Encrypt

### Medium term

8. **AI post generation** — LiteLLM gateway is configured; generate draft release notes from git diff or Jira tickets
9. **Full-text search** — `GET /changelog/{slug}?q=keyword` using the `tsvector` column already on posts
10. **File uploads** — MinIO integration for post header images
11. **Analytics frontend** — MRR charts, funnel visualisation, unit economics dashboard

### Long term

12. **Slack / Teams integration** — post to channel on publish
13. **SSO** — SAML for enterprise customers
14. **White-label** — custom branding per tenant (logo, colours, font)
15. **Multi-project org dashboard** — cross-project view for larger companies
