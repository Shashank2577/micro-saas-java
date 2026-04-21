CREATE TABLE workflow_templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    name        TEXT NOT NULL,
    description TEXT,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_by  UUID REFERENCES cc.users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workflow_template_steps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES workflow_templates(id) ON DELETE CASCADE,
    step_number     INT NOT NULL,
    name            TEXT NOT NULL,
    routing_mode    TEXT NOT NULL DEFAULT 'sequential', -- sequential | parallel
    approver_ids    UUID[] NOT NULL DEFAULT '{}',  -- specific user IDs
    approver_role   TEXT,  -- or any user with this RBAC role
    deadline_days   INT,   -- business days from step start
    require_all     BOOLEAN NOT NULL DEFAULT true  -- all approvers or any one
);

CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    title           TEXT NOT NULL,
    description     TEXT,
    document_type   TEXT,       -- contract | policy | invoice | proposal | other
    department      TEXT,
    current_version INT NOT NULL DEFAULT 1,
    originated_by   UUID REFERENCES cc.users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE document_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_number  INT NOT NULL,
    file_key        TEXT NOT NULL,    -- MinIO object key
    file_name       TEXT NOT NULL,
    file_size_bytes BIGINT,
    uploaded_by     UUID REFERENCES cc.users(id),
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workflow_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id),
    template_id     UUID REFERENCES workflow_templates(id),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    status          TEXT NOT NULL DEFAULT 'in_progress',
    -- in_progress | approved | rejected | cancelled
    current_step    INT NOT NULL DEFAULT 1,
    initiated_by    UUID REFERENCES cc.users(id),
    initiated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    audit_pdf_key   TEXT  -- MinIO key for generated audit PDF
);

CREATE TABLE workflow_step_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id     UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    step_number     INT NOT NULL,
    step_name       TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pending',
    -- pending | in_progress | approved | changes_requested | rejected | skipped
    assignee_id     UUID REFERENCES cc.users(id),
    assignee_email  TEXT,   -- for guest approvers
    action          TEXT,   -- approved | changes_requested | rejected
    comment         TEXT,
    deadline_at     TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);

-- Immutable audit log (never update rows)
CREATE TABLE approval_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    document_id UUID NOT NULL REFERENCES documents(id),
    workflow_id UUID REFERENCES workflow_instances(id),
    step_id     UUID REFERENCES workflow_step_instances(id),
    actor_id    UUID REFERENCES cc.users(id),
    actor_email TEXT,
    event_type  TEXT NOT NULL,
    -- document_uploaded | workflow_started | step_assigned | document_viewed
    -- approved | changes_requested | rejected | reminder_sent | workflow_completed
    metadata    JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
