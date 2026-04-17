CREATE TABLE drip_campaigns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    name            TEXT NOT NULL,
    trigger_event   TEXT NOT NULL,
    steps           JSONB NOT NULL DEFAULT '[]',
    status          TEXT NOT NULL DEFAULT 'active',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE drip_enrollments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    campaign_id     UUID NOT NULL REFERENCES drip_campaigns(id) ON DELETE CASCADE,
    customer_id     UUID NOT NULL,
    customer_email  TEXT NOT NULL,
    current_step    INT NOT NULL DEFAULT 0,
    status          TEXT NOT NULL DEFAULT 'active',
    enrolled_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_step_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_drip_enrollments_status_next ON drip_enrollments(status, next_step_at);
CREATE INDEX idx_drip_enrollments_customer ON drip_enrollments(customer_id, campaign_id);
CREATE INDEX idx_drip_campaigns_tenant ON drip_campaigns(tenant_id, trigger_event, status);
