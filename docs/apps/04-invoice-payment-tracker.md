# App 04: Invoice & Payment Tracker

**Tagline:** Create professional invoices, send them, and get paid — with zero accounting overhead.

**Category:** Finance / Freelancer Tools

---

## Problem Statement

Freelancers, consultants, and small agencies spend time they don't have on billing. They cobble together invoices in Word documents or Google Sheets, email them as PDFs, manually track which are paid, and send awkward follow-up emails when payment is late. The tools that solve this (FreshBooks, QuickBooks) are overkill and priced for full accounting departments.

The specific pains:
- "I haven't invoiced three clients yet this month" — billing falls behind because it's friction-heavy
- No visibility into outstanding receivables without opening a spreadsheet
- Late payments go weeks without a follow-up
- Clients pay wrong amounts because there's no structured way to annotate partial payments
- Generating a professional-looking PDF invoice in Word takes 20 minutes

The market need is for a sharp, minimal invoicing tool that handles the invoice-to-paid lifecycle without any of the accounting baggage.

---

## Target Users

**Primary Buyer / User:** Freelance developers, designers, copywriters, consultants — solo operators or 2–10 person studios

**Secondary Buyer:** Small agencies doing project-based billing (not retainer)

**Company Profile:**
- Self-employed individuals
- Small professional services firms (design, development, marketing)
- B2B service providers billing monthly project work

**Willingness to Pay:** $12–$30/month; price insensitive if it reliably gets them paid faster.

---

## Core Value Proposition

> Send a professional invoice in 2 minutes. Get notified when it's viewed. Get paid via link. Stop chasing.

---

## Feature Set

### MVP (Phase 1)

**Client Management**
- Add clients: name, company, email, billing address
- Store payment terms per client (Net 15, Net 30, etc.)
- Client history: all invoices ever sent to this client

**Invoice Creation**
- Line items: description, quantity, unit price, tax rate (per line)
- Invoice number (auto-incremented, configurable prefix)
- Due date based on payment terms or manual override
- Notes field (e.g., "Bank details: …" or "Thank you for your business")
- Invoice preview before sending
- PDF generation (server-side, clean professional template)
- Currency selection per invoice

**Sending & Tracking**
- Send invoice via email (generated PDF attached + HTML email with "View Invoice" button)
- Invoice opens a hosted payment page (public URL, no login required)
- Track: sent → viewed (pixel or link click) → payment initiated → paid
- "Viewed" timestamp recorded when client opens the payment page
- Team gets in-app notification when invoice is viewed

**Payment Collection**
- Payment link on hosted invoice page (Stripe or Dodo Payments via cc.payments)
- Client pays online — invoice auto-marked as paid, team notified
- Manual "Mark as Paid" for offline payments (bank transfer, cash)
- Partial payments: record payment amounts against an invoice

**Dashboard**
- Outstanding balance (total unpaid across all clients)
- Overdue invoices highlighted
- Recent payments timeline
- Quick stats: invoiced this month, collected this month, overdue count

**Reminders**
- Automatic reminder email on due date if not yet paid
- Second reminder 3 days after due date
- Manual "Send Reminder" button at any time

### Phase 2

- Recurring invoices (auto-generate monthly/quarterly)
- Invoice templates (save common line item sets)
- Multi-currency with FX display
- Expense tracking per project (deductible cost log)
- Client portal: clients see all their invoices in one place
- Bulk invoice sending (generate invoices for all retainer clients at month end)
- Stripe webhook for payment confirmation (vs. polling)
- Tax report export (for accountant: list of invoices, amounts, tax collected, by period)

### AI Features

- **Smart Line Item Suggestion:** Based on past invoices to the same client, AI suggests pre-filled line items ("Last time you billed Acme Corp for: Brand Design 40hrs @ $150/hr")
- **Payment Prediction:** Based on client history, flag invoices likely to go overdue ("Acme Corp pays late 80% of the time — consider sending a reminder now")
- **Late Payment Email Drafter:** Click "Draft Reminder" — AI writes a polite but firm reminder email in your voice, personalized to the specific invoice
- **Annual Summary Narrative:** AI generates a plain-English annual revenue summary ("You invoiced $94,500 in 2025, mostly from design work. Your best month was October. 3 clients account for 68% of revenue.")

---

## Data Model

```
tenants (via cross-cutting)
  └─ client (billing contacts)
  └─ invoice
       ├─ invoice_line_item
       ├─ payment (partial or full)
       └─ invoice_event (viewed, reminded, paid — audit trail)
  └─ invoice_settings (numbering, logo, bank details)
```

**Key Tables:**

```sql
-- app/V1__invoicing.sql
CREATE TABLE clients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
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
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
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
    recorded_by     UUID REFERENCES cc.users(id)
);

CREATE TABLE invoice_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id  UUID NOT NULL REFERENCES invoices(id),
    event_type  TEXT NOT NULL,   -- sent | viewed | reminded | payment_received | marked_paid
    metadata    JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invoice_settings (
    tenant_id       UUID PRIMARY KEY REFERENCES cc.tenants(id),
    next_number     INT NOT NULL DEFAULT 1,
    number_prefix   TEXT NOT NULL DEFAULT 'INV-',
    logo_key        TEXT,           -- MinIO key for logo
    default_notes   TEXT,
    bank_details    TEXT,
    default_currency TEXT NOT NULL DEFAULT 'USD'
);
```

---

## Cross-Cutting Module Mapping

| Module | How Used |
|--------|----------|
| **Multi-Tenancy** | Each freelancer/agency is a tenant; all clients and invoices are tenant-scoped |
| **Auth** | Team members authenticate via Keycloak; public invoice payment page is unauthenticated (token-based URL) |
| **RBAC** | Permissions: `invoices:create`, `invoices:send`, `invoices:delete`, `payments:record`. Useful when team has owner + bookkeeper roles |
| **Audit** | `@Audited` on `sendInvoice()`, `recordPayment()`, `cancelInvoice()` — essential for financial record-keeping |
| **Notifications** | Team notified: invoice viewed, payment received, invoice overdue. Client notified: invoice sent, payment confirmed |
| **Background Jobs** | Overdue detection job (daily cron: mark invoices past due_date as overdue). Reminder emails (scheduled per invoice). PDF generation (async, triggered on send) |
| **File Storage** | Generated PDF stored in MinIO per invoice. Tenant logo stored in MinIO |
| **Payments** | Stripe/Dodo integration via cc.payments for online payment collection on hosted invoice page |
| **Export** | CSV export of invoices for accountant, filtered by date range |
| **Feature Flags** | Gate "recurring invoices" and "AI features" behind paid plan |
| **AI Gateway** | Line item suggestions, payment prediction, late payment email drafter, annual summary |

---

## PDF Invoice Generation

PDF generation runs as a background job (not blocking the send request):

```
1. User clicks "Send Invoice"
2. Invoice status → "sent", job enqueued
3. JobHandler: InvoicePdfGenerator
   a. Fetch invoice + line items + client + tenant settings
   b. Render HTML template (Thymeleaf or jte) with invoice data
   c. Generate PDF via Flying Saucer / OpenPDF
   d. Upload PDF to MinIO: {tenantId}/invoices/{invoiceId}.pdf
   e. Update invoice.pdf_key
4. Email job: send email with PDF attachment + public payment link
5. Notification: in-app "Invoice #INV-042 sent to Acme Corp"
```

---

## API Design

```
# Clients
GET    /api/clients                         List clients
POST   /api/clients                         Create client
GET    /api/clients/{clientId}              Get client with invoice history
PUT    /api/clients/{clientId}              Update client
DELETE /api/clients/{clientId}              Archive client

# Invoices
GET    /api/invoices                        List invoices (filter: status, client, date)
POST   /api/invoices                        Create invoice (draft)
GET    /api/invoices/{invoiceId}            Get invoice
PUT    /api/invoices/{invoiceId}            Update draft invoice
POST   /api/invoices/{invoiceId}/send       Send invoice (generates PDF, sends email)
POST   /api/invoices/{invoiceId}/remind     Send reminder email
POST   /api/invoices/{invoiceId}/cancel     Cancel invoice
POST   /api/invoices/{invoiceId}/payments   Record manual payment

# Public invoice page (no auth)
GET    /pay/{publicToken}                   View invoice (records "viewed" event)
POST   /pay/{publicToken}/checkout          Initiate Stripe/Dodo checkout session

# Stripe webhook
POST   /webhooks/stripe                     Handle payment_intent.succeeded

# Dashboard
GET    /api/dashboard/summary               Outstanding, collected, overdue totals

# Settings
GET    /api/settings                        Invoice settings (prefix, logo, etc.)
PUT    /api/settings                        Update settings
POST   /api/settings/logo                   Upload logo (presigned URL)
```

---

## Frontend Screens

| Screen | Purpose |
|--------|---------|
| **Dashboard** | Receivables overview: outstanding, overdue, recent payments timeline |
| **Invoice List** | All invoices with status badges, filter/sort, quick actions |
| **New Invoice** | Client selector, line item table, notes, due date, currency, preview |
| **Invoice Detail** | Status timeline, events log, payments list, send/remind/cancel actions |
| **Client List** | All clients with outstanding balance per client |
| **Client Detail** | Invoice history for one client |
| **Public Payment Page** | Clean, branded page with invoice details and pay button (Stripe Checkout) |
| **Settings** | Invoice numbering, logo, default notes, bank details |

---

## Monetization

| Plan | Price | Limits |
|------|-------|--------|
| **Free** | $0 | 5 invoices/month, PDF download only (no online payment) |
| **Solo** | $12/month | Unlimited invoices, online payment links, reminders, CSV export |
| **Studio** | $28/month | Everything + recurring invoices, AI features, multi-user (3 seats), client portal |

**Transaction Fee Option:** Alternatively, charge 0.5% of payment volume on free plan instead of a monthly fee (aligned incentive — we earn when they get paid).

---

## Competitive Landscape

| Competitor | Gap |
|------------|-----|
| **FreshBooks** | Full accounting suite — overkill; $17–$55/month; too many features |
| **Wave** | Free invoicing but UI is clunky; Canadian focus; no AI features |
| **Invoice Ninja** | Open source; good but poor UX; self-hosted complexity |
| **Bonsai** | Strong competitor at $17/month; also includes contracts — differentiate on cleaner UX and AI features |
| **PayPal Invoices** | Free but terrible branding; no tracking; tied to PayPal ecosystem |

**Differentiation:** Invoice "viewed" tracking (instant visibility into when client opens it), AI-powered late payment prediction, clean professional PDF output, and line item suggestions from history — all at a price point under $30/month.

---

## Build Phases

### Phase 1 — MVP (8 weeks)
- Client CRUD
- Invoice CRUD with line items
- PDF generation (background job, Thymeleaf → PDF)
- Email sending via Resend (PDF attached)
- Public payment page (token-based URL)
- Stripe Checkout integration
- Manual "Mark as Paid" and payment recording
- Invoice viewed tracking
- Dashboard with outstanding/overdue summary
- Overdue detection cron job
- Automatic reminder emails

### Phase 2 — Growth (5 weeks)
- Recurring invoices (monthly/quarterly auto-generation)
- Invoice templates (saved line item sets)
- Client portal (client sees all their invoices)
- Tax report CSV export
- Multi-user support with bookkeeper role

### Phase 3 — Scale
- AI line item suggestions (past invoice history)
- AI payment prediction (flag likely-late clients)
- AI late payment email drafter
- Annual summary narrative
- Multi-currency support with FX rates

---

## Success Metrics

| Stage | Signal |
|-------|--------|
| **MVP launched** | 50 invoices sent in first 30 days |
| **Adoption** | 40%+ of invoices sent have "viewed" status within 48 hours |
| **Payment** | 30%+ of invoices paid online (vs. manual mark-as-paid) |
| **Monetization** | 20% of active users upgrade by month 2 |
| **Retention** | 70% of paid users active at 90 days |
