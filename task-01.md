# MISSION
You are an autonomous principal engineer. Your mission is to implement the assigned application as a new module in this monorepo.

# ARCHITECTURE RULES
1. **Module Location**: Create the app under apps/01-client-portal-builder.
2. **Dependency**: Your pom.xml MUST have saas-os-parent as the parent and saas-os-core as a dependency.
3. **Core Reuse**: Use classes from com.changelog.* in saas-os-core for Billing, AI, and Multi-tenancy.
4. **Java Standard**: Use Java 21 only.

# SPECIFICATION
# App 01: Client Portal Builder

**Tagline:** White-label client portals for agencies, consultants, and service businesses.

**Category:** Collaboration / Client Management

---

## Problem Statement

Agencies, freelancers, and consultants share deliverables with clients through email threads, Google Drive folders, and Slack messages. This creates a scattered, unprofessional experience: clients hunt for the latest version of a file, miss approval requests, and lose context. The service provider looks disorganized.

The specific pains:
- Deliverables buried in email chains ("which version is final?")
- No single place for the client to see what's been done and what's pending
- Approval requests lost in Slack or never formally tracked
- No audit trail of who approved what and when
- Onboarding each new client requires manual setup

This is a $0 problem to ignore short-term but causes client churn and scope creep long-term.

---

## Target Users

**Primary Buyer:** Agency owner / solo consultant / freelancer (2–50 person teams)

**Primary User:** Project manager or account manager at the agency

**End User (client side):** Client stakeholder who reviews and approves work

**Company Profile:**
- Digital marketing agencies
- UX/design studios
- Software development shops
- Legal and accounting consultancies
- Brand agencies

**Willingness to Pay:** $40–$120/month per agency workspace. Clients access for free (no seat cost to the client — this is a key differentiator vs. tools that charge per external user).

---

## Core Value Proposition

> Give every client a dedicated, branded portal — one URL where they see all deliverables, leave feedback, and sign off on work. Your agency looks polished; nothing gets lost.

---

## Feature Set

### MVP (Phase 1)

**Portal Management**
- Create a named portal per client engagement
- Assign a custom subdomain (e.g., `acmecorp.yourportal.com`) or custom domain
- Upload your logo and brand color — applied to the client-facing portal
- Invite clients via email link (no account required for clients — magic link access)

**Deliverables**
- Upload files (PDFs, images, videos, documents) per portal
- Organize deliverables into named sections (e.g., "Brand Guidelines", "Website Mockups")
- Version history — upload a new version, old versions archived
- Presigned download URLs (direct from storage, no server bandwidth used)

**Approval Workflow**
- Mark any deliverable as "Needs Approval"
- Client clicks Approve or Request Changes
- If Request Changes: client leaves a comment
- Agency gets notified immediately (in-app + email)
- Approval status visible on dashboard: Pending / Approved / Changes Requested

**Messaging**
- Per-portal comment thread (not per file — one conversation per engagement)
- Threaded replies
- Agency side marked separately from client side
- Email notifications for new messages

**Dashboard (Agency View)**
- All portals with status: Active / Awaiting Approval / All Approved
- Last activity timestamp per portal
- Items awaiting client action highlighted

### Phase 2

- Embed portal as an iframe on your own website
- Custom email domain for notifications (send as `hello@youragency.com`)
- Client activity log (when did they view what)
- PDF generation of approved deliverables with stamp
- Public share links (no login) for specific deliverables
- Due dates on approvals with automatic reminder emails
- Multiple workspaces per agency account (for sub-brands or multiple team members)

### AI Features

- **AI Brief Summarizer:** Paste a long client brief into the portal; AI generates a structured summary (goals, deliverables, timeline, open questions) for internal use
- **Feedback Tone Analyzer:** When a client leaves a "Request Changes" comment, AI classifies the feedback as technical / aesthetic / scope and suggests a professional agency response
- **Deliverable Description Writer:** Given a file name and type, AI drafts a 2–3 sentence client-facing description to accompany the upload

---

## Data Model

```
tenants (via cross-cutting)
  └─ workspace (1:1 with tenant, holds branding config)
       └─ portal (many per workspace)
            ├─ portal_member (junction: portal ↔ client user, role: VIEWER/APPROVER)
            ├─ section (ordered groupings within a portal)
            │    └─ deliverable (file, version, status)
            │         └─ deliverable_version (file_key, uploaded_at, uploader)
            ├─ approval (deliverable_id, status, reviewed_by, reviewed_at, comment)
            └─ message (portal_id, author_id, author_role: AGENCY/CLIENT, body, parent_id)
```

**Key Tables (app migration):**

```sql
-- app/V1__portals.sql
CREATE TABLE portals (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
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
    uploaded_by     UUID REFERENCES cc.users(id),
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
```

---

## Cross-Cutting Module Mapping

| Module | How Used |
|--------|----------|
| **Multi-Tenancy** | Each agency is a tenant; all portals, deliverables, messages scoped to `tenant_id` |
| **Auth** | Agency staff authenticate via Keycloak; clients authenticate via magic link (custom lightweight token, not Keycloak) |
| **RBAC** | Agency roles: `org_admin` (billing), `member` (project manager). Permissions: `portals:read`, `portals:write`, `deliverables:upload`, `approvals:manage` |
| **Audit** | `@Audited` on `approveDeliverable()`, `uploadDeliverable()`, `inviteClient()` — creates legally relevant audit trail |
| **Notifications** | Notify agency: client approved, client requested changes, new client message. Notify client: new deliverable uploaded, approval reminder |
| **Webhooks** | Agencies can register webhooks for `deliverable.approved`, `deliverable.changes_requested`, `message.created` — integrate with Slack, Zapier |
| **File Storage** | Presigned upload URLs for deliverable files; tenant-isolated (`{tenantId}/portals/{portalId}/{versionId}`) |
| **Background Jobs** | Send approval reminder emails (scheduled job); generate PDF approval summary after all items approved |
| **Feature Flags** | Gate "custom domain" and "AI features" behind paid plan flag |
| **AI Gateway** | Brief summarizer, feedback analyzer, description writer — routed through LiteLLM proxy |
| **Export** | Export portal activity log as CSV (for agency records) |

---

## API Design

```
# Portal management (agency-authenticated)
GET    /api/portals                          List all portals for tenant
POST   /api/portals                          Create portal
GET    /api/portals/{portalId}               Get portal details
PUT    /api/portals/{portalId}               Update portal (name, branding)
DELETE /api/portals/{portalId}               Archive portal

# Sections & deliverables
POST   /api/portals/{portalId}/sections      Add section
POST   /api/portals/{portalId}/deliverables  Add deliverable (with presigned upload URL)
PUT    /api/deliverables/{id}                Update deliverable metadata
POST   /api/deliverables/{id}/versions       Upload new version (returns presigned URL)

# Approvals
POST   /api/deliverables/{id}/approve        Client approves (client-token authenticated)
POST   /api/deliverables/{id}/request-changes  Client requests changes with comment

# Messages
GET    /api/portals/{portalId}/messages      Get message thread
POST   /api/portals/{portalId}/messages      Post message

# Client access (magic-link authenticated, no Keycloak)
POST   /api/portals/access                   Exchange magic token for session
GET    /api/client/portals/{portalId}        Client view of portal

# Invites
POST   /api/portals/{portalId}/invites       Send magic link invite to client email
```

---

## Frontend Screens

| Screen | Purpose |
|--------|---------|
| **Dashboard** | All portals, status badges, last activity |
| **New Portal** | Name, client name, branding upload |
| **Portal Editor** | Manage sections, upload deliverables, track approval status |
| **Message Center** | Per-portal comment thread with agency/client role badges |
| **Client Portal View** | Clean, branded view for client — download files, approve, comment |
| **Settings** | Workspace branding, custom domain, webhook endpoints |

---

## Monetization

| Plan | Price | Limits |
|------|-------|--------|
| **Starter** | $0 | 2 portals, 500 MB storage, yourportal.com subdomain |
| **Pro** | $49/month | Unlimited portals, 20 GB storage, custom subdomain, webhook support |
| **Agency** | $119/month | Everything in Pro + custom domain, AI features, PDF approval export, priority support |

**Billing Model:** Monthly/annual subscription per agency workspace. Clients always free.

---

## Competitive Landscape

| Competitor | Gap This App Fills |
|------------|-------------------|
| **Basecamp** | Too heavy, general-purpose project management, no approval workflow |
| **Notion** | Not purpose-built for client delivery; no approval states |
| **Google Drive** | No branding, no approval workflow, unprofessional look |
| **Copilot (useCopilot.com)** | Strong competitor — differentiate on simplicity, lower price, no per-client seat fees |
| **Clinked** | Expensive ($119/month+), dated UI |
| **SuiteDash** | Overwhelming — tries to do everything |

**Differentiation:** Zero-cost client seats (clients never pay or sign up), magic-link access (no friction), clean modern UI, built-in approval workflow with audit trail, webhook integration for agency automations.

---

## Build Phases

### Phase 1 — MVP (8 weeks)
- Portal CRUD with branding
- Section + deliverable management
- File upload via presigned URLs (MinIO)
- Magic link client access
- Approval workflow (approve / request changes)
- Per-portal message thread
- Email notifications (new upload, new message, approval)
- Tenant onboarding (signup → create workspace → invite team)

### Phase 2 — Growth (6 weeks)
- Custom subdomains
- Client activity log
- Approval reminders (background job scheduler)
- Webhook endpoints UI
- PDF approval summary export

### Phase 3 — Scale
- Custom email domain (via Resend)
- AI features (brief summarizer, feedback analyzer)
- Embeddable portal widget
- API for programmatic portal creation

---

## Success Metrics

| Stage | Signal |
|-------|--------|
| **MVP launched** | 10 agencies with ≥1 active portal |
| **Early traction** | 30% of portals have client activity within 7 days of creation |
| **Monetization** | 20% free→paid conversion at 2-month mark |
| **Retention** | Monthly churn below 5% |
| **Growth** | Agencies average 4+ portals active simultaneously |
