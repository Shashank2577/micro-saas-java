-- apps/08-api-key-management-portal/src/main/resources/db/migration/V1__api_keys.sql

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

-- High-volume usage tracking
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
);

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
