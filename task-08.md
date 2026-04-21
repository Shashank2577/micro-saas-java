# MISSION
You are an autonomous principal engineer. Your mission is to implement the assigned application as a new module in this monorepo.

# ARCHITECTURE RULES
1. **Module Location**: Create the app under apps/08-api-key-management-portal.
2. **Dependency**: Your pom.xml MUST have saas-os-parent as the parent and saas-os-core as a dependency.
3. **Core Reuse**: Use classes from com.changelog.* in saas-os-core for Billing, AI, and Multi-tenancy.
4. **Java Standard**: Use Java 21 only.

# SPECIFICATION
# App 08: API Key Management Portal

**Tagline:** Give your customers a self-service API key dashboard — without building one yourself.

**Category:** Developer Tools / Platform Infrastructure

---

## Problem Statement

When a SaaS company builds a developer-facing product or public API, they need to give their customers API keys. Most teams handle this by bolting on a basic key-generation screen in their settings page — a one-time hack that quickly becomes a liability. There is no rotation, no scoping, no usage visibility, no audit trail, and no way for customers to manage multiple environments.

The specific pains:
- Developer support tickets: "Can you regenerate my API key? I accidentally committed it"
- No per-key usage visibility: "How many calls did we make this month?" — unknown
- No key scoping: one key has full access; can't give a vendor read-only access
- Security incidents from stale API keys that should have been rotated months ago
- Building this properly takes 2–4 weeks of engineering time — a distraction from core product
- Teams that sell to enterprise get requirements: "Provide audit logs of all API key usage"

The gap: a SaaS product specifically for SaaS companies — you embed our widget or point your customers to our portal, and they get a professional API key management experience while you get the backend control.

---

## Target Users

**Primary Buyer (the SaaS company):** CTO or Head of Platform Engineering at a B2B SaaS company with a developer-facing API

**End User (the customer of that SaaS):** Developer at a company that uses the SaaS product's API

**Company Profile (buyer):**
- SaaS companies with a public API or developer platform
- Platforms that give API access to enterprise customers
- API-first products (data providers, AI services, fintech, etc.)
- Teams at the "we should have done this properly six months ago" stage

**Willingness to Pay:** $50–$300/month depending on number of active API consumers (the SaaS company's customers).

---

## Core Value Proposition

> Your customers get a professional self-service API key portal in one day. You get usage analytics and security controls without building them.

---

## Feature Set

### MVP (Phase 1)

**API Key Lifecycle Management (customer-facing)**
- Customer creates named API keys ("Production Key", "CI/CD Pipeline", "Vendor Read Access")
- Key displayed once on creation, never again (stored as bcrypt hash)
- Key rotation: create new key → verify it works → revoke old key (overlap window)
- Key revocation: immediate, with confirmation
- Key status: Active / Revoked / Expired
- Key metadata: name, created date, last used date, created by user

**Key Scopes / Permissions (optional, buyer configures)**
- Buyer defines available scopes for their API (e.g., `read:users`, `write:data`, `admin`)
- Customer selects scopes at key creation
- Platform embeds the scopes in the key's JWT payload or in a lookup table

**Usage Dashboard (customer-facing)**
- Requests today / this week / this month per key
- Success vs. error rate per key
- Top endpoints called per key
- Rate limit status (if the buyer configures per-key rate limits)

**Admin Console (buyer-facing)**
- View all customers' keys across the platform
- Search by customer, key name, or prefix
- Revoke any key (emergency: security incident)
- Usage aggregated by customer
- Keys not used in 90 days: flag for cleanup

**Embeddable Widget**
- JavaScript snippet that embeds the key management UI inside the buyer's own product
- Matches the buyer's color scheme (primary color, font)
- Opens in an iframe or inline mount point

**Audit Log (per customer, per key)**
- Immutable log: key created, key used (timestamp + endpoint), key revoked, key rotated
- Downloadable as CSV (for compliance)

### Phase 2

- Key expiry dates (auto-expire keys after N days — good for vendors)
- IP allowlist per key (only accept requests from these IPs)
- Per-key rate limits (buyer sets: "free tier keys: 100 req/min; enterprise keys: 10,000 req/min")
- Webhook events on key creation, revocation, and rate limit hit
- Multi-environment keys (Production / Staging / Development grouped per customer)
- API to manage keys programmatically (for CI/CD: auto-rotate keys before deployment)
- SAML/SSO support for enterprise customers logging in to manage their keys

### AI Features

- **Anomaly Detection:** AI monitors usage patterns per key. If a key suddenly makes 50x its normal request volume, or starts calling endpoints it never used before, alert the customer and the admin ("Unusual activity on key 'Production-v2' — 400 calls in the last 5 minutes from a new IP")
- **Stale Key Report:** AI analyzes key usage and generates a weekly "you have 3 keys that haven't been used in 90 days — consider revoking them to reduce your attack surface"
- **Risk Score per Key:** Based on age, last rotation, scope breadth, and IP diversity, assign each key a security risk score with remediation suggestions

---

## Data Model

```
tenants (via cross-cutting)
  = the SaaS companies (buyers) using this platform

  └─ api_consumer (the buyer's customers, each with their own portal)
       └─ api_key (keys created by that consumer)
            └─ key_usage_event (request log — high volume)
  └─ scope_definition (buyer defines available scopes for their API)
  └─ portal_config (buyer's embeddable widget config)
```

**Key Tables:**

```sql
-- app/V1__api_keys.sql
CREATE TABLE api_consumers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),   -- the SaaS company
    external_id     TEXT NOT NULL,   -- the ID from the buyer's own user system
    name            TEXT NOT NULL,
    email           TEXT,
    plan_tier       TEXT,            -- free | starter | enterprise (buyer-defined)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, external_id)
);

CREATE TABLE scope_definitions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    name        TEXT NOT NULL,      -- e.g., "read:users"
    description TEXT,
    UNIQUE(tenant_id, name)
);

CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    consumer_id     UUID NOT NULL REFERENCES api_consumers(id),
    name            TEXT NOT NULL,
    key_prefix      TEXT NOT NULL,   -- first 8 chars for display (e.g., "sk_live_")
    key_hash        TEXT NOT NULL,   -- bcrypt hash of full key
    scopes          TEXT[] NOT NULL DEFAULT '{}',
    environment     TEXT NOT NULL DEFAULT 'production',  -- production | staging | development
    status          TEXT NOT NULL DEFAULT 'active',      -- active | revoked | expired
    last_used_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,     -- null = no expiry
    ip_allowlist    TEXT[],          -- null = no restriction
    rate_limit_rpm  INT,             -- requests per minute limit, null = default
    created_by      TEXT,            -- consumer's user ID from their system
    revoked_at      TIMESTAMPTZ,
    revoked_by      TEXT,
    rotation_of     UUID REFERENCES api_keys(id),  -- points to key this replaced
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- High-volume usage tracking (consider partitioning by month)
CREATE TABLE key_usage_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_id          UUID NOT NULL REFERENCES api_keys(id),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    endpoint        TEXT NOT NULL,
    method          TEXT NOT NULL,
    status_code     INT NOT NULL,
    response_ms     INT,
    ip_address      INET,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
) PARTITION BY RANGE (occurred_at);

-- Summary table for dashboard (pre-aggregated, refreshed hourly)
CREATE TABLE key_usage_summary (
    key_id          UUID NOT NULL REFERENCES api_keys(id),
    period          TEXT NOT NULL,       -- 'day' | 'week' | 'month'
    period_start    DATE NOT NULL,
    request_count   BIGINT NOT NULL DEFAULT 0,
    error_count     BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (key_id, period, period_start)
);

-- Immutable audit log
CREATE TABLE key_audit_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_id      UUID REFERENCES api_keys(id),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    consumer_id UUID REFERENCES api_consumers(id),
    event_type  TEXT NOT NULL,
    -- key_created | key_viewed | key_revoked | key_rotated | key_expired
    -- key_used | rate_limited | ip_blocked
    actor       TEXT,    -- consumer user ID or admin user ID
    metadata    JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE portal_config (
    tenant_id       UUID PRIMARY KEY REFERENCES cc.tenants(id),
    primary_color   TEXT NOT NULL DEFAULT '#4F46E5',
    logo_url        TEXT,
    app_name        TEXT NOT NULL DEFAULT 'API Keys',
    allowed_origins TEXT[] NOT NULL DEFAULT '{}',  -- for iframe embedding
    available_scopes_enabled BOOLEAN NOT NULL DEFAULT false
);
```

---

## Cross-Cutting Module Mapping

| Module | How Used |
|--------|----------|
| **Multi-Tenancy** | Each SaaS company (buyer) is a tenant. Their customers are `api_consumers` within that tenant |
| **Auth** | Buyer admins authenticate via Keycloak. Consumer portal is authenticated via signed JWT issued by the buyer's platform (consumer passes their own user token) |
| **RBAC** | Permissions: `keys:create`, `keys:revoke`, `keys:view_all_consumers`, `admin:revoke_any`. Role: `admin` (buyer's team), `consumer` (customer managing their own keys only) |
| **Audit** | `key_audit_events` is the core compliance trail. `@Audited` on `revokeKey()`, `createKey()`, `rotateKey()` |
| **Notifications** | Consumer: key nearing expiry. Admin: unusual usage detected. Consumer: key revoked by admin. Rate limit threshold hit |
| **Security** | Rate limiting per key (enforced at API gateway level or middleware). Field-level encryption for sensitive key metadata |
| **Background Jobs** | Hourly usage aggregation job (populate `key_usage_summary`). Daily stale key detection job. Key expiry job (auto-revoke expired keys). AI anomaly detection job |
| **Webhooks** | Events: `key.created`, `key.revoked`, `key.expired`, `anomaly.detected` — buyer's platform can react |
| **Feature Flags** | Gate "IP allowlist", "per-key rate limits", "AI anomaly detection" behind paid plan |
| **AI Gateway** | Anomaly detection (pattern analysis), stale key report, risk scoring |
| **Export** | Per-consumer audit log CSV export (compliance requirement) |

---

## API Design

```
# Buyer API (Keycloak-authenticated, buyer's admin team)
GET    /api/consumers                         List all API consumers
GET    /api/consumers/{consumerId}            Get consumer with keys
POST   /api/consumers                         Register consumer (manual or via buyer's webhook)
POST   /api/consumers/{consumerId}/keys/{keyId}/revoke  Emergency revoke

GET    /api/keys                              List all keys (admin view)
GET    /api/analytics/usage                   Platform-wide usage summary
GET    /api/analytics/stale-keys              Keys not used in 90+ days

# Consumer Portal API (JWT from buyer's system)
GET    /portal/keys                           List my keys
POST   /portal/keys                           Create new key → {plaintext key} (shown once)
POST   /portal/keys/{keyId}/revoke            Revoke key
POST   /portal/keys/{keyId}/rotate            Rotate key → {new plaintext key}
GET    /portal/keys/{keyId}/usage             Usage stats for key
GET    /portal/keys/{keyId}/audit             Audit log for key

# Portal config
GET    /api/portal-config                     Get widget config
PUT    /api/portal-config                     Update widget config (colors, logo, scopes)

# Key validation endpoint (for buyer to call from their API gateway)
POST   /validate                              Validate API key → {consumer_id, scopes, rate_limit}
# This is the hot path — must be <10ms; Redis-cached after first lookup

# Scope definitions
GET    /api/scopes                            List defined scopes
POST   /api/scopes                            Define a new scope
DELETE /api/scopes/{scopeId}                  Remove scope
```

---

## Frontend Screens

| Screen | Audience | Purpose |
|--------|----------|---------|
| **Admin Console** | Buyer's team | All consumers, all keys, usage overview, emergency revoke |
| **Consumer Portal** | API consumer (customer) | Manage their own keys, view usage, rotate keys |
| **New Key Modal** | Consumer | Name key, select scopes, environment — key shown once |
| **Key Detail** | Consumer | Usage chart, audit log, expiry, IP allowlist |
| **Usage Analytics** | Admin | Request volume by consumer, error rates, top endpoints |
| **Portal Customizer** | Admin | Configure widget colors, logo, available scopes |
| **Stale Key Report** | Admin | Keys not used in 90 days with one-click revoke |

---

## Monetization

| Plan | Price | Limits |
|------|-------|--------|
| **Starter** | $49/month | Up to 50 API consumers, basic usage stats |
| **Growth** | $149/month | Up to 500 consumers, IP allowlist, per-key rate limits, webhooks |
| **Scale** | $299/month | Unlimited consumers, AI anomaly detection, audit CSV export, SLA, SSO |

**Pricing note:** Charged to the SaaS company (buyer), not their end customers. One tenant = one buyer = one monthly invoice.

---

## Competitive Landscape

| Competitor | Gap |
|------------|-----|
| **Build it yourself** | Takes 2–4 weeks; still missing anomaly detection, rotation UX, audit logs |
| **AWS API Gateway** | Infrastructure-level; no customer-facing portal; requires AWS commitment |
| **Kong / Tyk** | API gateway-level products; expensive; no customer-facing key portal |
| **Treblle** | API observability; not a key management portal |
| **Zuplo** | Closer competitor; $20/month entry; no AI anomaly detection; limited customization |

**Differentiation:** Embeddable widget (drop into your existing product settings page), AI anomaly detection that surfaces suspicious key usage automatically, and a validation endpoint fast enough (<10ms with Redis caching) to be in the hot path of every API request.

---

## Build Phases

### Phase 1 — MVP (9 weeks)
- Tenant onboarding (buyer registers, configures portal)
- Consumer registration (via API or manual)
- Key creation (bcrypt hash stored, plaintext shown once)
- Key revocation and rotation
- Consumer portal (list keys, create, revoke, rotate)
- Key validation endpoint (Redis-cached, hot path)
- Usage tracking (raw events table)
- Basic admin console
- Embeddable JavaScript widget (iframe)

### Phase 2 — Growth (6 weeks)
- Usage aggregation jobs (hourly summary)
- Usage dashboard (charts per key)
- Key expiry and auto-revocation
- IP allowlist per key
- Per-key rate limiting
- Webhooks (key.created, key.revoked, anomaly.detected)
- Audit log CSV export

### Phase 3 — Scale
- AI anomaly detection (usage pattern baseline + deviation detection)
- AI stale key report with risk scoring
- Per-consumer rate limit tiers
- SAML/SSO for enterprise consumers
- Multi-region key validation (latency optimization)

---

## Success Metrics

| Stage | Signal |
|-------|--------|
| **MVP launched** | 5 SaaS companies (buyers) onboarded, each with ≥10 consumers |
| **Usage** | Key validation endpoint serving ≥10K requests/day per buyer |
| **Security value** | 20%+ of keys rotated at least once in first 90 days |
| **AI value** | Anomaly alerts triggered and acknowledged (not dismissed) |
| **Monetization** | Buyers stay 6+ months (sticky infrastructure product) |
