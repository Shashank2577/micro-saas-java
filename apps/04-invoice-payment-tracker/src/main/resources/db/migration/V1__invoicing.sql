-- app/V1__invoicing.sql
CREATE TABLE clients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            TEXT NOT NULL,
    company         TEXT,
    email           TEXT NOT NULL,
    address         TEXT,
    payment_terms   INT NOT NULL DEFAULT 30,  -- days
    currency        TEXT NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    client_id       UUID NOT NULL REFERENCES clients(id),
    invoice_number  TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'draft',
    -- draft | sent | viewed | partial | paid | overdue | cancelled
    currency        TEXT NOT NULL DEFAULT 'USD',
    subtotal        NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_total       NUMERIC(12,2) NOT NULL DEFAULT 0,
    total           NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_paid     NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes           TEXT,
    issue_date      DATE,
    due_date        DATE,
    sent_at         TIMESTAMPTZ,
    viewed_at       TIMESTAMPTZ,    -- first view timestamp
    paid_at         TIMESTAMPTZ,
    public_token    TEXT NOT NULL UNIQUE,  -- token for public payment page
    pdf_key         TEXT,                  -- MinIO key for generated PDF
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, invoice_number)
);

CREATE TABLE invoice_line_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description     TEXT NOT NULL,
    quantity        NUMERIC(10,3) NOT NULL DEFAULT 1,
    unit_price      NUMERIC(12,2) NOT NULL,
    tax_rate        NUMERIC(5,2) NOT NULL DEFAULT 0,
    amount          NUMERIC(12,2) NOT NULL,  -- quantity * unit_price
    position        INT NOT NULL DEFAULT 0
);

CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      UUID NOT NULL REFERENCES invoices(id),
    amount          NUMERIC(12,2) NOT NULL,
    method          TEXT NOT NULL DEFAULT 'online',  -- online | manual
    reference       TEXT,            -- Stripe payment intent ID or note
    paid_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by     UUID REFERENCES users(id)
);

CREATE TABLE invoice_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id  UUID NOT NULL REFERENCES invoices(id),
    event_type  TEXT NOT NULL,   -- sent | viewed | reminded | payment_received | marked_paid
    metadata    JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invoice_settings (
    tenant_id       UUID PRIMARY KEY REFERENCES tenants(id),
    next_number     INT NOT NULL DEFAULT 1,
    number_prefix   TEXT NOT NULL DEFAULT 'INV-',
    logo_key        TEXT,           -- MinIO key for logo
    default_notes   TEXT,
    bank_details    TEXT,
    default_currency TEXT NOT NULL DEFAULT 'USD'
);
