-- ============================================
-- Changelog Platform Database Schema
-- ============================================

-- Create schema for cross-cutting (shared infrastructure)
CREATE SCHEMA IF NOT EXISTS cc;

-- Tenants table (multi-tenancy foundation)
CREATE TABLE cc.tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    slug            TEXT NOT NULL UNIQUE,
    plan_tier       TEXT NOT NULL DEFAULT 'free',  -- free | startup | growth | scale
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Users table (Keycloak integration)
CREATE TABLE cc.users (
    id              UUID PRIMARY KEY,  -- This comes from Keycloak
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    email           TEXT NOT NULL,
    name            TEXT,
    role            TEXT NOT NULL DEFAULT 'editor',  -- admin | editor | publisher
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, email)
);

-- ============================================
-- Changelog-specific tables
-- ============================================

CREATE TABLE changelog_projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    name            TEXT NOT NULL,
    slug            TEXT NOT NULL,
    description     TEXT,
    logo_key        TEXT,      -- MinIO key for project logo
    favicon_key     TEXT,
    custom_domain   TEXT,
    branding        JSONB NOT NULL DEFAULT '{}',  -- colors, font
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);

CREATE TABLE changelog_tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES changelog_projects(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    color       TEXT NOT NULL DEFAULT '#6B7280',
    UNIQUE(project_id, name)
);

CREATE TABLE changelog_posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL REFERENCES changelog_projects(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    title           TEXT NOT NULL,
    summary         TEXT,
    content         TEXT NOT NULL DEFAULT '',     -- markdown
    content_tsv     tsvector GENERATED ALWAYS AS
                    (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(content,''))) STORED,
    header_image_key TEXT,
    status          TEXT NOT NULL DEFAULT 'draft',  -- draft | scheduled | published
    published_at    TIMESTAMPTZ,
    scheduled_for   TIMESTAMPTZ,
    author_id       UUID REFERENCES cc.users(id),
    view_count      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_changelog_posts_tsv ON changelog_posts USING GIN(content_tsv);
CREATE INDEX idx_changelog_posts_status ON changelog_posts(status);
CREATE INDEX idx_changelog_posts_published ON changelog_posts(published_at DESC);

CREATE TABLE post_tag_assignments (
    post_id     UUID NOT NULL REFERENCES changelog_posts(id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES changelog_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);

CREATE TABLE changelog_subscribers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES changelog_projects(id) ON DELETE CASCADE,
    email       TEXT NOT NULL,
    name        TEXT,
    plan_tier   TEXT,       -- for segmentation (populated by buyer via API)
    status      TEXT NOT NULL DEFAULT 'active',  -- active | unsubscribed
    subscribed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unsubscribed_at TIMESTAMPTZ,
    UNIQUE(project_id, email)
);

CREATE TABLE subscriber_notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES changelog_posts(id),
    subscriber_id   UUID NOT NULL REFERENCES changelog_subscribers(id),
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    opened_at       TIMESTAMPTZ,   -- email open tracking pixel
    clicked_at      TIMESTAMPTZ
);

CREATE TABLE widget_configs (
    project_id      UUID PRIMARY KEY REFERENCES changelog_projects(id),
    position        TEXT NOT NULL DEFAULT 'bottom-right',  -- bottom-right | bottom-left | top-right
    trigger_type    TEXT NOT NULL DEFAULT 'badge',         -- badge | button
    badge_label     TEXT NOT NULL DEFAULT 'What''s New',
    primary_color   TEXT NOT NULL DEFAULT '#4F46E5',
    allowed_origins TEXT[] NOT NULL DEFAULT '{}'
);

-- ============================================
-- Audit logging (compliance)
-- ============================================

CREATE TABLE cc.audit_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    user_id     UUID REFERENCES cc.users(id),
    action      TEXT NOT NULL,     -- post.created | post.published | post.deleted | project.created
    entity_type TEXT NOT NULL,
    entity_id   UUID NOT NULL,
    metadata    JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_events_tenant ON cc.audit_events(tenant_id, occurred_at DESC);
