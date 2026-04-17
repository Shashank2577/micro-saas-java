# Platform Framework Reference

This document describes everything the underlying platform provides to every micro-SaaS app built here. App-specific docs reference this file when explaining which infrastructure is "free" vs. what each app must build itself.

---

## Stack Overview

```
┌──────────────────────────────────────────────────────────┐
│                  Your Micro-SaaS App                     │
│  (Spring Boot controllers + services + domain entities)  │
├──────────────────────────────────────────────────────────┤
│               cross-cutting (cc-starter)                 │
│  16 auto-configured modules (Maven dependency, Java 21)  │
├──────────────────────────────────────────────────────────┤
│                    Infrastructure                        │
│  PostgreSQL 16 · Redis 7 · Keycloak 24 · MinIO          │
├──────────────────────────────────────────────────────────┤
│                   freestack (deploy)                     │
│  OCI VM · Vercel · Neon · GitHub Actions · Cloudflare    │
└──────────────────────────────────────────────────────────┘
```

---

## cross-cutting Modules

### 1. Multi-Tenancy (`cc.tenancy`)
- Thread-local `TenantContext` holding current tenant UUID
- `TenantFilter` resolves tenant from `X-Tenant-ID` header or `tenant_id` JWT claim
- Automatic per-tenant data scoping — all queries filtered by tenant
- Configurable modes: `multi` (default) or `single`
- Database: `cc.tenants`, `cc.tenant_memberships`, `cc.tenant_config`

**Apps use this by:** calling `TenantContext.require()` inside service methods and filtering all queries by `tenantId`.

---

### 2. Authentication (`cc.auth`)
- Keycloak OIDC / OAuth2 JWT validation
- `UserSyncFilter` auto-creates/updates user records from JWT claims on each request
- `CcPrincipal.current()` provides `userId`, `email`, `tenantId`, `roles`
- Public paths excluded from auth: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`

**Apps use this by:** relying on `CcPrincipal.current()` — no token parsing needed.

---

### 3. RBAC (`cc.rbac`)
- `@RequirePermission(resource = "projects", action = "read")` — declarative method-level enforcement
- `RbacService` with Redis cache (5-min TTL)
- Permission format: `resource:action` (e.g., `invoices:write`, `*:*`)
- Seeded system roles: `super_admin`, `org_admin`, `member`
- Custom app-level permissions registered via `cc.rbac.app-permissions`

**Apps use this by:** annotating controller methods and registering their resource/action pairs in `application.yml`.

---

### 4. Audit Logging (`cc.audit`)
- **System audit:** `SystemAuditFilter` automatically logs every HTTP request to `audit.system_audit_log`
- **Business audit:** `@Audited(action = "CREATE_INVOICE", resourceType = "invoice")` on service methods
- Captures: method, path, status, duration, userId, tenantId, IP, user agent, correlation ID
- Sensitive field redaction: password, secret, token, apiKey, authorization
- Tables: `audit.system_audit_log`, `audit.business_audit_log`

**Apps use this by:** adding `@Audited` to important business operations — everything else is captured automatically.

---

### 5. Structured Logging (`cc.logging`)
- `CorrelationIdFilter` generates/propagates `X-Correlation-ID` header
- MDC enriched with: `correlationId`, `tenantId`, `userId`
- Log pattern: `%5p [%X{correlationId}] [%X{tenantId}] [%X{userId}]`
- Connects audit logs, app logs, and distributed traces

---

### 6. Feature Flags (`cc.flags`)
- Per-tenant feature toggles
- Override hierarchy: user > tenant > global > default
- Redis-cached (5-min TTL)
- Use to gate features behind paid plans or gradual rollouts

**Apps use this by:** wrapping premium features in `featureFlagService.isEnabled("feature-name")`.

---

### 7. Notifications (`cc.notifications`)
- Built-in channels: `InAppChannel`, `EmailChannel`
- Custom channels via `NotificationChannel` interface
- Per-user preference management (opt-out, channel selection)
- `notificationService.send(userId, type, data)`

**Apps use this by:** defining notification types and calling the service from business logic or job handlers.

---

### 8. Webhooks (`cc.webhooks`)
- Outbound webhook dispatch: `webhookService.dispatch(tenantId, eventType, payload)`
- HMAC-SHA256 signing via `X-Webhook-Signature` header
- Automatic retry with configurable attempts
- Tables: `webhooks.webhook_endpoints`, `webhooks.webhook_deliveries`

**Apps use this by:** dispatching events from service methods — tenants register their own endpoints via the app's webhook UI.

---

### 9. Background Job Queue (`cc.queue`)
- PostgreSQL-backed with `SKIP LOCKED` (no Redis dependency)
- Implement `JobHandler` interface: `getQueueName()`, `handle(Job)`
- Auto-retry up to 5 attempts, dead-letter support
- Scheduled poller via `JobWorker` (enable with `cc.queue.worker-enabled=true`)

**Apps use this by:** enqueuing jobs for async tasks (email sending, PDF generation, data export, AI processing).

---

### 10. File Storage (`cc.storage`)
- MinIO (S3-compatible) with presigned upload/download URLs
- Tenant-isolated object key pattern: `{tenantId}/{bucket}/{fileName}`
- Methods: `getUploadUrl()`, `getDownloadUrl()`, `deleteFile()`, `listFiles()`

**Apps use this by:** generating presigned URLs for direct browser-to-storage uploads, avoiding server memory overhead.

---

### 11. Search (`cc.search`)
- Full-text search via PostgreSQL `tsvector` indexes
- Semantic vector search via `pgvector` extension
- Combine keyword + semantic for hybrid retrieval

**Apps use this by:** indexing content into tsvector columns and, for AI apps, storing embeddings in pgvector columns.

---

### 12. Data Export (`cc.export`)
- Async CSV/JSON export jobs
- Background queue integration
- Tables: `cc.export_jobs`

**Apps use this by:** offering "Export to CSV" on list views — no custom implementation needed.

---

### 13. AI Gateway (`cc.ai`)
- LiteLLM proxy as a unified gateway
- Supports: OpenAI, Anthropic, Groq, Mistral, and local models
- Per-tenant usage metering possible
- Configuration: `cc.ai.gateway-url`, `cc.ai.master-key`

**Apps use this by:** making HTTP calls to the LiteLLM proxy instead of calling OpenAI directly — one config, any model.

---

### 14. Payments (`cc.payments`)
- Payment processing integration hooks
- Configurable payment provider (Stripe, Dodo, etc.)
- Subscription lifecycle events

---

### 15. Security (`cc.security`)
- Rate limiting: `RateLimitFilter` with Redis (configurable limit per tenant)
- Input sanitization: `InputSanitizer`
- Field-level encryption: configurable with `cc.security.encryption-key`
- CORS: configured via `cc.security.cors-origins`

---

### 16. Error Handling (`cc.error`)
- `CcException(errorCode, message, httpStatus)` — uniform exception
- Global `@ExceptionHandler` for consistent REST error responses
- Standard error codes: `TENANT_NOT_FOUND`, `UNAUTHORIZED`, `FORBIDDEN`, `RATE_LIMITED`, etc.

---

## freestack Deployment Platform

### Environments

| Branch | Environment | Backend URL | Frontend URL | Compute |
|--------|-------------|-------------|--------------|---------|
| `dev` | Development | `api-dev.{domain}` | `dev.{domain}` | OCI VM3 :8080 |
| `qa` | Staging | `api-qa.{domain}` | `qa.{domain}` | OCI VM3 :8081 |
| `main` | Production | `api.{domain}` | `{domain}` | OCI VM1 + VM2 |

### Third-Party Services (all free tier)

| Service | Role | Cost |
|---------|------|------|
| **OCI ARM VMs** | Backend compute (2 prod VMs, 1 dev/qa VM) | $0 |
| **Vercel** | Next.js frontend hosting | $0 |
| **Neon** | PostgreSQL (branched per environment) | $0 |
| **GitHub Actions** | CI/CD + image builds (GHCR) | $0 |
| **Cloudflare** | DNS, DDoS, optional R2 storage | $0 |
| **Sentry** | Error tracking (Java + Next.js) | $0 |
| **Better Stack** | Uptime monitoring | $0 |
| **Resend** | Transactional email | $0 |

### Deployment Flow (per push)

```
git push main
  → GitHub Actions: build Docker image (linux/arm64)
  → Push image to GHCR
  → SSH to VM2: docker compose up, health check
  → SSH to VM1: docker compose up, health check
  → Rollback if any health check fails
```

### Starting a New App

```bash
# 1. Use freestack as GitHub template
# 2. Run init.sh — fill in PROJECT_NAME, DOMAIN, GITHUB_ORG
# 3. Add repository secrets (Terraform cloud, OCI SSH key, etc.)
# 4. Trigger bootstrap.yml workflow once (provisions all environments)
# 5. Add cc-starter Maven dependency to pom.xml
# 6. Write Spring Boot controllers + domain code
# 7. Push to dev/qa/main branches to deploy
```

---

## What Apps Must Build Themselves

Despite the rich platform, each app must implement its own:

- **Domain entities** — the core business data model (e.g., `Invoice`, `Project`, `OKR`)
- **Business logic** — the rules and workflows specific to the app
- **API controllers** — REST endpoints beyond the platform's built-in `/cc/**` routes
- **Frontend** — Next.js pages and components (platform provides React SDK hooks: `useAuth`, `usePermission`, `useFeatureFlag`, `useNotifications`)
- **App-specific migrations** — Flyway files in `classpath:db/migration/app`
- **Custom RBAC permissions** — registering `resource:action` pairs for the app's domain
- **Notification types** — defining what events trigger notifications and their templates
- **Webhook event types** — defining what domain events are published as outbound webhooks
