-- app/V1__portals.sql
CREATE TABLE portals (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    name        TEXT NOT NULL,
    slug        TEXT NOT NULL,
    client_name TEXT,
    status      TEXT NOT NULL DEFAULT 'active', -- active | archived
    branding    JSONB NOT NULL DEFAULT '{}',    -- logo_url, primary_color
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, slug)
);

CREATE TABLE portal_sections (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portal_id   UUID NOT NULL REFERENCES portals(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    position    INT NOT NULL DEFAULT 0
);

CREATE TABLE deliverables (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id      UUID NOT NULL REFERENCES portal_sections(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    description     TEXT,
    status          TEXT NOT NULL DEFAULT 'pending', -- pending | approved | changes_requested
    current_version INT NOT NULL DEFAULT 1,
    requires_approval BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE deliverable_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deliverable_id  UUID NOT NULL REFERENCES deliverables(id) ON DELETE CASCADE,
    version_number  INT NOT NULL,
    file_key        TEXT NOT NULL,  -- MinIO object key
    file_name       TEXT NOT NULL,
    file_size_bytes BIGINT,
    uploaded_by     UUID,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE approvals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deliverable_id  UUID NOT NULL REFERENCES deliverables(id),
    version_number  INT NOT NULL,
    status          TEXT NOT NULL,  -- approved | changes_requested
    reviewed_by     UUID,           -- client user id (may not be in cc.users)
    reviewer_email  TEXT,
    comment         TEXT,
    reviewed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE portal_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portal_id   UUID NOT NULL REFERENCES portals(id) ON DELETE CASCADE,
    author_id   UUID,
    author_role TEXT NOT NULL,  -- agency | client
    body        TEXT NOT NULL,
    parent_id   UUID REFERENCES portal_messages(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE portal_invites (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portal_id   UUID NOT NULL REFERENCES portals(id),
    email       TEXT NOT NULL,
    token       TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);
