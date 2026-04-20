-- app/V10_1__okrs.sql

-- Cycles
CREATE TABLE okr_cycles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    name        TEXT NOT NULL,    -- "Q2 2026"
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    status      TEXT NOT NULL DEFAULT 'planning', -- planning | active | completed
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Objectives
CREATE TABLE objectives (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    cycle_id        UUID NOT NULL REFERENCES okr_cycles(id),
    parent_id       UUID REFERENCES objectives(id),  -- null = company-level
    title           TEXT NOT NULL,
    description     TEXT,
    level           TEXT NOT NULL DEFAULT 'team',   -- company | department | team | individual
    owner_id        UUID,
    department      TEXT,
    status          TEXT NOT NULL DEFAULT 'on_track', -- on_track | at_risk | off_track
    progress        NUMERIC(5,2) NOT NULL DEFAULT 0,  -- 0-100% (calculated from KRs)
    final_score     NUMERIC(3,2),   -- 0.0-1.0, set at quarter end
    final_note      TEXT,
    is_public       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Key Results
CREATE TABLE key_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    objective_id    UUID NOT NULL REFERENCES objectives(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    title           TEXT NOT NULL,
    description     TEXT,
    kr_type         TEXT NOT NULL DEFAULT 'metric',  -- metric | milestone | percentage
    owner_id        UUID,
    -- For metric KRs
    start_value     NUMERIC(15,2),
    target_value    NUMERIC(15,2),
    current_value   NUMERIC(15,2),
    unit            TEXT,   -- "$", "%", "users", etc.
    -- For milestone KRs
    is_completed    BOOLEAN NOT NULL DEFAULT false,
    -- Computed
    progress        NUMERIC(5,2) NOT NULL DEFAULT 0,   -- 0-100%
    confidence      TEXT NOT NULL DEFAULT 'on_track',  -- on_track | at_risk | off_track
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Check-ins
CREATE TABLE kr_check_ins (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_result_id   UUID NOT NULL REFERENCES key_results(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    owner_id        UUID,
    week_start      DATE NOT NULL,   -- Monday of the check-in week
    -- For metric KRs
    current_value   NUMERIC(15,2),
    -- For milestone KRs
    is_completed    BOOLEAN,
    -- For all
    progress_pct    NUMERIC(5,2) NOT NULL,  -- progress at time of check-in
    confidence      TEXT NOT NULL,           -- on_track | at_risk | off_track
    note            TEXT NOT NULL,           -- mandatory: why did this change?
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(key_result_id, week_start)
);

-- Reminders
CREATE TABLE check_in_reminders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    week_start      DATE NOT NULL,
    user_id         UUID,
    sent_at         TIMESTAMPTZ,
    reminder_sent_at TIMESTAMPTZ,   -- Tuesday noon follow-up
    checked_in_at   TIMESTAMPTZ
);

-- Indexes for performance and tenant isolation
CREATE INDEX idx_okr_cycles_tenant ON okr_cycles(tenant_id);
CREATE INDEX idx_objectives_tenant ON objectives(tenant_id);
CREATE INDEX idx_objectives_cycle ON objectives(cycle_id);
CREATE INDEX idx_key_results_tenant ON key_results(tenant_id);
CREATE INDEX idx_key_results_objective ON key_results(objective_id);
CREATE INDEX idx_kr_check_ins_tenant ON kr_check_ins(tenant_id);
CREATE INDEX idx_kr_check_ins_kr ON kr_check_ins(key_result_id);
CREATE INDEX idx_check_in_reminders_tenant ON check_in_reminders(tenant_id);
