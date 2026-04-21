CREATE TABLE onboarding_templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    name        TEXT NOT NULL,
    description TEXT,
    category    TEXT NOT NULL DEFAULT 'onboarding',
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE template_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES onboarding_templates(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    description     TEXT,
    task_type       TEXT NOT NULL DEFAULT 'complete',
    assignee_type   TEXT NOT NULL DEFAULT 'new_hire',
    due_day_offset  INT NOT NULL DEFAULT 1,
    resource_url    TEXT,
    resource_name   TEXT,
    is_required     BOOLEAN NOT NULL DEFAULT true,
    position        INT NOT NULL DEFAULT 0
);

CREATE TABLE onboarding_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    template_id     UUID REFERENCES onboarding_templates(id),
    hire_name       TEXT NOT NULL,
    hire_email      TEXT NOT NULL,
    hire_role       TEXT,
    department      TEXT,
    manager_id      UUID,
    buddy_id        UUID,
    start_date      DATE NOT NULL,
    status          TEXT NOT NULL DEFAULT 'active',
    portal_token    TEXT NOT NULL UNIQUE,
    portal_opened_at TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE task_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    onboarding_id   UUID NOT NULL REFERENCES onboarding_instances(id) ON DELETE CASCADE,
    template_task_id UUID REFERENCES template_tasks(id),
    title           TEXT NOT NULL,
    description     TEXT,
    task_type       TEXT NOT NULL,
    assignee_id     UUID,
    assignee_email  TEXT,
    due_date        DATE NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pending',
    completed_at    TIMESTAMPTZ,
    completed_by    TEXT
);

CREATE TABLE task_submissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_instance_id UUID NOT NULL REFERENCES task_instances(id),
    response_text   TEXT,
    file_key        TEXT,
    file_name       TEXT,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
