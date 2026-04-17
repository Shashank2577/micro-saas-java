# Micro-SaaS Applications — Java

> 10 production-grade micro-SaaS products. One repository. One shared infrastructure. Independently monetisable.

A portfolio of 10 SaaS applications built with Spring Boot 3.3.5 and Java 25. Each product solves a distinct business problem and can be sold standalone at the $20–$200/month price point.

---

## Repository Status

| # | App | Status | Revenue Model | Spec | Code |
|---|-----|--------|--------------|------|------|
| **09** | **Changelog & Release Notes Platform** | **In Progress** | $0–$199/mo | [spec](docs/apps/09-changelog-platform.md) | [README](changelog-platform/README.md) |
| 02 | Team Feedback & Roadmap | Planned (next) | $29–$99/mo | [spec](docs/apps/02-team-feedback-roadmap.md) | — |
| 07 | Lightweight Issue Tracker | Planned | $19–$79/mo | [spec](docs/apps/07-lightweight-issue-tracker.md) | — |
| 08 | API Key Management Portal | Planned | $49–$199/mo | [spec](docs/apps/08-api-key-management-portal.md) | — |
| 01 | Client Portal Builder | Planned | $49–$119/mo | [spec](docs/apps/01-client-portal-builder.md) | — |
| 03 | AI Knowledge Base | Planned | $15–$60/mo | [spec](docs/apps/03-ai-knowledge-base.md) | — |
| 04 | Invoice & Payment Tracker | Planned | $9–$29/mo | [spec](docs/apps/04-invoice-payment-tracker.md) | — |
| 05 | Document Approval Workflow | Planned | $29–$89/mo | [spec](docs/apps/05-document-approval-workflow.md) | — |
| 06 | Employee Onboarding Orchestrator | Planned | $5–$15/employee/mo | [spec](docs/apps/06-employee-onboarding-orchestrator.md) | — |
| 10 | OKR & Goal Tracker | Planned | $6–$15/user/mo | [spec](docs/apps/10-okr-goal-tracker.md) | — |

---

## What's Been Built (App 09)

App 09 — Changelog & Release Notes Platform — is the foundation. It's being built first because it contains the **SaaS Operating System** that all 10 apps will share.

### Core Product (Done)

| Feature | What's Built |
|---------|-------------|
| Projects | Full CRUD, slugs, JSONB branding config, custom domain field |
| Posts | Draft / Scheduled / Published lifecycle, markdown content, full-text search column |
| Tags | Category tags (New Feature, Bug Fix, etc.) per project |
| Public changelog | `GET /changelog/{slug}` — unauthenticated, paginated, published-only |
| In-app widget config | Position, colours, allowed origins stored per project |
| Subscriber management | Opt-in/out, plan-tier segmentation |

### SaaS Operating System (Done)

| Module | What's Built |
|--------|-------------|
| Acquisition | Landing pages, A/B variant testing, conversion tracking |
| Monetisation | Stripe products, subscriptions, full webhook handler |
| Retention | Drip campaign engine — templates, enrollments, hourly step processor |
| Success | Customer health scoring (0–100 + risk signals), support tickets (AI-classified, sentiment scored) |
| Intelligence | Analytics events, funnel analysis, unit economics (MRR/ARR/CAC/LTV) |
| Multi-tenancy | All rows scoped to `tenant_id`, Keycloak JWT extracts tenant context |
| Auth | Keycloak OAuth2 resource server; `TenantResolver` pattern (local vs. production) |
| Database | Flyway V1–V4 migrations, 20+ tables, JSONB for flexible metadata |
| Local dev | Docker Compose (postgres:5433, keycloak, minio), `local` Spring profile (no Keycloak) |

### Not Yet Built (App 09)

| Feature | Notes |
|---------|-------|
| JavaScript widget | CDN-hosted `<script>` embed, "What's New" bell icon |
| Email delivery | Subscriber notifications on publish; drip sends (currently logged only) |
| RSS feed | `GET /changelog/{slug}/feed.xml` |
| Frontend UI | React or Thymeleaf admin dashboard and public page |
| Custom domain | CNAME + SSL via Let's Encrypt |
| Scheduled publishing | `scheduledFor` field exists; background job not wired |
| AI post generation | LiteLLM gateway configured; integration pending |
| Stripe checkout | Products modelled; checkout session flow not built |
| Full-text search API | `tsvector` column exists; search endpoint not exposed |
| File uploads (MinIO) | Config present; upload endpoint missing |

---

## Project Vision

```
Goal: Prove one developer can ship 10 commercially viable SaaS products
      sharing one infrastructure foundation.

Each app = Core product feature + SaaS Operating System

SaaS Operating System (built in app 09, reused in apps 01–08, 10):
  ┌─────────────────┐
  │   Acquisition   │  Landing pages, A/B testing, conversion tracking
  ├─────────────────┤
  │  Monetisation   │  Stripe subscriptions, webhooks, product catalogue
  ├─────────────────┤
  │   Retention     │  Drip email campaigns triggered by lifecycle events
  ├─────────────────┤
  │    Success      │  Health scoring, support tickets
  ├─────────────────┤
  │  Intelligence   │  Analytics, funnels, unit economics dashboard
  └─────────────────┘
```

---

## Build Sequence

```
09 Changelog Platform   ← CURRENT (SaaS OS built here, full foundation)
02 Team Feedback        ← reuses SaaS OS, adds voting + roadmap
07 Issue Tracker        ← reuses SaaS OS, adds bug workflow
08 API Key Management   ← developer tools niche
01 Client Portal        ← services niche
03 AI Knowledge Base    ← requires LLM integration work
04 Invoice Tracker      ← finance niche
05 Document Approval    ← compliance niche
06 Employee Onboarding  ← HR niche
10 OKR Tracker          ← strategy niche
```

Each subsequent app reuses the SaaS OS wholesale. Only the core domain model changes.

---

## App Catalogue

### 09 — Changelog & Release Notes Platform
> Publish product updates professionally and automatically notify everyone who cares.

**Status:** In Progress — [full README](changelog-platform/README.md)

A Beamer/Headway alternative. SaaS companies ship features constantly but communicate them poorly — customers miss new features, form an impression the product is stagnant, and churn even when the team ships weekly. This fixes that with a public changelog page, embeddable "What's New" widget, and automatic subscriber emails.

**Target:** B2B SaaS companies with 50–5000 customers  
**Price:** Free → $199/month (4 tiers)  
**Spec:** [docs/apps/09-changelog-platform.md](docs/apps/09-changelog-platform.md)

---

### 02 — Team Feedback & Roadmap
> Collect feature requests, let users vote, publish a public roadmap — all in one place.

**Status:** Planned — next to build

A Canny/Productboard alternative. Users submit ideas, vote on others, and see a public roadmap that updates automatically when features ship. Closes the loop between customer requests and product decisions.

**Target:** B2B SaaS product teams  
**Price:** $29–$99/month  
**Spec:** [docs/apps/02-team-feedback-roadmap.md](docs/apps/02-team-feedback-roadmap.md)

---

### 07 — Lightweight Issue Tracker
> Report bugs, track them to resolution, get notified when done.

**Status:** Planned

A Linear/Jira alternative for teams who need bug tracking without complexity. Public bug reporting form, internal triage, status updates sent to reporters automatically.

**Target:** Small engineering teams, indie developers  
**Price:** $19–$79/month  
**Spec:** [docs/apps/07-lightweight-issue-tracker.md](docs/apps/07-lightweight-issue-tracker.md)

---

### 08 — API Key Management Portal
> Give your customers a self-service API key dashboard — without building one yourself.

**Status:** Planned

If you have an API product, your customers need to create/rotate/revoke keys. This feature pre-built. Embed it in your app in a day instead of building it in a month.

**Target:** Developer tool companies, API-first products  
**Price:** $49–$199/month  
**Spec:** [docs/apps/08-api-key-management-portal.md](docs/apps/08-api-key-management-portal.md)

---

### 01 — Client Portal Builder
> White-label client portals for agencies, consultants, and service businesses.

**Status:** Planned

Give clients a branded portal where they can view project status, approve deliverables, download files, and communicate — without email chains.

**Target:** Agencies, consultants, service businesses  
**Price:** $49–$119/month  
**Spec:** [docs/apps/01-client-portal-builder.md](docs/apps/01-client-portal-builder.md)

---

### 03 — AI Knowledge Base
> Your team's internal wiki — search it in plain English, get answers, not just links.

**Status:** Planned

A Notion/Confluence alternative with semantic search. Ask a question in natural language, get a direct answer with citations.

**Target:** Teams with 20–500 people  
**Price:** $15–$60/month  
**Spec:** [docs/apps/03-ai-knowledge-base.md](docs/apps/03-ai-knowledge-base.md)

---

### 04 — Invoice & Payment Tracker
> Create professional invoices, send them, and get paid — with zero accounting overhead.

**Status:** Planned

Simple invoicing for freelancers and small agencies. Create invoice → send to client → client pays online → you get notified.

**Target:** Freelancers, small agencies  
**Price:** $9–$29/month  
**Spec:** [docs/apps/04-invoice-payment-tracker.md](docs/apps/04-invoice-payment-tracker.md)

---

### 05 — Document Approval Workflow
> Route any document for review, collect approvals, and maintain a full audit trail.

**Status:** Planned

A lightweight DocuSign alternative for internal approval workflows — contracts, policies, SOPs. Multi-step approval chains with email notifications and a full audit log.

**Target:** Operations, legal, compliance teams  
**Price:** $29–$89/month  
**Spec:** [docs/apps/05-document-approval-workflow.md](docs/apps/05-document-approval-workflow.md)

---

### 06 — Employee Onboarding Orchestrator
> Structured onboarding plans that run themselves.

**Status:** Planned

Build onboarding checklists once, assign them to new hires, and watch tasks auto-assign to IT/HR/managers on day 1.

**Target:** HR teams at 50–500 person companies  
**Price:** $5–$15/employee/month  
**Spec:** [docs/apps/06-employee-onboarding-orchestrator.md](docs/apps/06-employee-onboarding-orchestrator.md)

---

### 10 — OKR & Goal Tracker
> Set ambitious objectives, track key results weekly, and align your whole team.

**Status:** Planned

A lightweight Lattice/Perdoo alternative. Define company OKRs, cascade to teams and individuals, check in weekly, see a dashboard of what's on track vs. at risk.

**Target:** Teams of 20–500 people  
**Price:** $6–$15/user/month  
**Spec:** [docs/apps/10-okr-goal-tracker.md](docs/apps/10-okr-goal-tracker.md)

---

## Shared Infrastructure

All 10 apps share the same stack — no duplicated setup:

| Component | Technology |
|-----------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 3.3.5 |
| Database | PostgreSQL 16 + Flyway |
| Auth | Keycloak 24 (OAuth2/OIDC) |
| Payments | Stripe |
| File storage | MinIO (S3-compatible) |
| Email | Spring Mail (SMTP) |
| Build | Maven |
| Local dev | Docker Compose |
| JSON columns | Hibernate 6 + JSONB |

---

## Getting Started

Only app 09 is implemented. Start there:

```bash
cd changelog-platform
docker compose up -d          # postgres:5433, keycloak:8080, minio:9000
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Verify it works

```bash
# Public changelog (from seed data)
curl http://localhost:8081/changelog/demo-project

# Create a project and post
curl -X POST http://localhost:8081/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{"name":"Acme SaaS","slug":"acme-saas","description":"Updates"}'

curl -X POST http://localhost:8081/api/v1/projects/{projectId}/posts \
  -H "Content-Type: application/json" \
  -d '{"title":"v1.0 Launch","summary":"First release","content":"# Whats new\n\n- Feature A"}'

curl -X POST http://localhost:8081/api/v1/posts/{postId}/publish

curl http://localhost:8081/changelog/acme-saas
# → {"projectName":"Acme SaaS","totalPosts":1,"posts":[...]}
```

Full setup guide: [changelog-platform/README.md](changelog-platform/README.md)

---

## Documentation

| Document | Purpose |
|----------|---------|
| [changelog-platform/README.md](changelog-platform/README.md) | Full implementation guide for app 09 |
| [docs/README.md](docs/README.md) | Master index of all app specs |
| [docs/apps/](docs/apps/) | Detailed spec for each of the 10 apps |
