# System Architecture

## Overview

Changelog Platform is a multi-tenant SaaS operating system built on Spring Boot 3.3.5, PostgreSQL 16, and Hibernate 6.x.

```
┌─────────────────────────────────────────────────────────────────┐
│                      REST API Layer (8081)                       │
│  ProjectController | AnalyticsController | StripeController ... │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                  Security & Middleware                           │
│         JWT Authentication | Tenant Isolation Filter             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│              Service Layer (Business Logic)                      │
│                                                                  │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │ Analytics   │  │ Monetization │  │ Operations   │            │
│  │ Service     │  │ Service      │  │ Service      │            │
│  └─────────────┘  └──────────────┘  └──────────────┘            │
│                                                                  │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │ AI Service  │  │ Legal Service │  │ Support Srv  │            │
│  └─────────────┘  └──────────────┘  └──────────────┘            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                  Repository Layer (Data Access)                  │
│  ProjectRepository | AnalyticsEventRepository | ...             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   PostgreSQL 16 (JSONB Storage)                 │
│                                                                  │
│  Tables with JSONB Columns:                                     │
│  - analytics_events (properties)                                 │
│  - funnel_analytics (steps, step_counts, conversion_rates)      │
│  - stripe_products (features, metadata)                         │
│  - stripe_subscriptions (metadata)                              │
│  - changelog_projects (branding)                                │
│  - ai_conversations (messages, metadata)                        │
│  - support_tickets (custom_fields)                              │
│  - customer_health_scores (signals)                             │
└─────────────────────────────────────────────────────────────────┘
```

## Key Components

### Multi-Tenancy

Every table has a `tenant_id` column. The `TenantIsolationFilter` extracts tenant from JWT token and enforces isolation at the repository level via Spring Data JPA custom queries.

### JSONB Storage

PostgreSQL JSONB columns store flexible, nested data:

- **AnalyticsEvent.properties**: Custom event metadata (`{source: "product_hunt", campaign: "launch_2024"}`)
- **FunnelAnalytics**: Step definitions, conversion rates, dropoff points
- **StripeProduct**: Features list, tier metadata
- **AIConversation**: Message history with role and timestamp
- **CustomerHealthScore**: Signal tracking (usage trends, error counts, etc.)

Hibernate 6.x requires `@JdbcTypeCode(SqlTypes.JSON)` annotations for all Map/List fields mapping to JSONB.

## Modules

### 1. Analytics Module (Intelligence)
- Tracks user behavior (acquisition, monetization, retention, referral)
- Funnel analysis with conversion rates and bottleneck detection
- JSONB properties for flexible event metadata

### 2. Monetization Module
- Stripe product catalog (pricing, features, intervals)
- Subscription lifecycle (trial, active, canceled, past_due)
- Webhook handling for payment events
- Metadata for subscription-level customization

### 3. Operations Module
- Support ticket management (status, priority, assignment)
- Customer health scoring (overall score, risk level, signals)
- Proactive churn detection

### 4. AI Module
- Multi-turn conversations with context preservation
- LiteLLM gateway integration for multiple model support
- Token usage tracking and cost estimation

### 5. Legal Module
- GDPR data deletion requests
- Legal document management
- Compliance tracking

## Security

- **JWT Authentication**: Stateless, signed with RS256 (RSA)
- **Tenant Isolation**: Filter enforces all queries include `tenant_id` from token
- **CORS**: Configured for production domains
- **HTTPS Only**: Enforced in production

## Scalability Considerations

- Read replicas for analytics queries
- Connection pooling (HikariCP)
- Caching layer (Redis) for frequently accessed data
- JSONB indexing for fast properties queries
- Partitioning of analytics_events by date

## Database Schema

### Core Tables

| Table | Purpose | Key Fields |
|-------|---------|-----------|
| `changelog_projects` | Project metadata | tenant_id, name, branding |
| `users` | User accounts | tenant_id, email, password_hash |
| `sessions` | User sessions | tenant_id, user_id, jwt_token |
| `view_logs` | View tracking | tenant_id, user_id, timestamp |

### Business Module Tables

| Table | Module | JSONB Fields |
|-------|--------|-------------|
| `analytics_events` | Analytics | properties |
| `funnel_analytics` | Analytics | steps, step_counts, conversion_rates, dropoff_points |
| `stripe_products` | Monetization | features, metadata |
| `stripe_subscriptions` | Monetization | metadata |
| `support_tickets` | Operations | custom_fields |
| `customer_health_scores` | Operations | signals |
| `ai_conversations` | AI | messages, metadata |
| `legal_documents` | Legal | content, metadata |
| `gdpr_requests` | Legal | status_history, data_dump |

## API Design Principles

1. **RESTful**: Resources as nouns (/projects, /analytics, /tickets)
2. **Tenant-Scoped**: All endpoints implicitly scoped to authenticated tenant
3. **JSONB-Native**: Accept/return flexible JSON structures for metadata
4. **Versioning**: URI versioning (/api/v1, /api/v2) for breaking changes
5. **Pagination**: Limit/offset for large datasets

## Testing Strategy

- **Unit Tests**: Service and repository layer
- **Integration Tests**: Controller + service + repository
- **E2E Tests**: Full workflow validation (signup → activity → health score)
- **Performance Tests**: JSONB query performance at scale

## Deployment

See `DEPLOYMENT.md` for production deployment steps.
