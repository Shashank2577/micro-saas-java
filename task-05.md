# MISSION
You are an autonomous principal engineer. Your mission is to implement the assigned application as a new module in this monorepo.

# ARCHITECTURE RULES
1. **Module Location**: Create the app under apps/05-document-approval-workflow.
2. **Dependency**: Your pom.xml MUST have saas-os-parent as the parent and saas-os-core as a dependency.
3. **Core Reuse**: Use classes from com.changelog.* in saas-os-core for Billing, AI, and Multi-tenancy.
4. **Java Standard**: Use Java 21 only.

# SPECIFICATION
# App 05: Document Approval Workflow

**Tagline:** Route any document for review, collect approvals, and maintain a full audit trail — without the DocuSign complexity.

**Category:** Operations / Compliance

---

## Problem Statement

Every organization has documents that need to be reviewed and approved before they can be acted on: contracts, policies, proposals, budgets, HR letters, SOPs, purchase orders. The current process involves emailing PDFs back and forth, getting lost in "RE: RE: RE: Final_v3_REVIEWED_FINAL.pdf" threads, and having no idea who approved what and when.

The specific pains:
- Approval chain is ad hoc — someone always gets skipped
- No single record of who signed off and when (compliance risk)
- Version confusion: reviewers comment on different versions of the same document
- Approving a document via email is not legally auditable
- DocuSign and Adobe Sign are expensive ($25–$50/seat/month) and built for e-signatures, not multi-step internal approval workflows
- SharePoint approval flows exist but require an IT department to configure

The gap: a lightweight tool that handles multi-step document routing with a full timestamped approval history, without requiring e-signature infrastructure.

---

## Target Users

**Primary Buyer:** Operations manager, HR lead, Legal/Compliance officer

**Primary User:** Anyone who originates documents needing approval (e.g., HR creating offer letters, procurement team creating POs, legal team routing contracts)

**Approvers:** Internal employees who review and approve/reject documents

**Company Profile:**
- 50–500 person companies without a dedicated legal ops platform
- Companies in regulated industries (healthcare, finance, manufacturing) needing process documentation
- Agencies or consultancies where proposals need internal sign-off before sending to clients

**Willingness to Pay:** $50–$200/month flat, or $8–$15/seat/month for active approvers.

---

## Core Value Proposition

> Define who needs to approve what, route documents through the chain, and get a timestamped approval record — in minutes, not days.

---

## Feature Set

### MVP (Phase 1)

**Document Upload**
- Upload any file type (PDF, DOCX, XLSX, images) as the document to approve
- Document title, description, and metadata (type, department)
- Version tracking: upload a revised version, old version archived, reviewers notified

**Approval Workflow Builder**
- Define approval templates: named steps with assignees and order
  - Example: "Contract Approval" → Step 1: Legal Review → Step 2: CFO Sign-off → Step 3: CEO Final Approval
- Two routing modes:
  - **Sequential:** Step 2 can't start until Step 1 is complete
  - **Parallel:** All reviewers notified simultaneously; need all (or N of M) to approve
- Per-step deadline (e.g., "Legal must review within 3 business days")
- Ad hoc workflow: initiate one-off approval without a saved template

**Review Interface**
- Reviewer receives email with document link
- In-app review page: view the document (PDF inline viewer for PDFs), leave a comment
- Action buttons: **Approve** / **Request Changes** / **Reject**
- Approval with optional digital annotation: "Approved, but see comment on section 3"
- Rejection requires a reason

**Status Tracking**
- Originator sees full pipeline status: pending steps, completed steps, overall progress
- Timeline view: who did what and when
- If a step is overdue: automatic reminder to that approver
- Dashboard: "Awaiting your approval" tray (inbox-style)

**Audit Trail**
- Every action permanently recorded: opened document, approved, rejected, commented, re-routed
- Tamper-evident: records stored with created_at timestamp and user ID, never mutable
- Export approval report as PDF: document name, workflow steps, each approver's action, timestamps

### Phase 2

- E-signature collection (draw signature or type name — not legally binding but sufficient for internal approval)
- Conditional routing: "If CFO approves, skip CEO step unless amount > $50K"
- Bulk approvals (approve multiple documents at once)
- Delegation: "I'm on vacation — route my approvals to Alice until Dec 10"
- Integration with HR systems (BambooHR, Workday) via webhook
- Reminder escalation: if approver doesn't act in 5 days, auto-escalate to their manager
- Guest approvers (external party approves via email link, no account required)

### AI Features

- **Document Classifier:** When a document is uploaded, AI reads the first page and suggests the appropriate approval template based on document type (contract → "Contract Approval", invoice > $10K → "Large Purchase Approval")
- **Risk Flagger:** AI scans the document text and highlights unusual clauses or red flags for the legal reviewer ("Section 4.2 includes an unlimited liability clause — unusual for this contract type")
- **Summary for Approvers:** AI generates a 3-bullet summary of the document so approvers can quickly understand what they're approving without reading 40 pages
- **SLA Prediction:** Based on each approver's historical response time, AI predicts when the workflow will complete ("Based on your team's history, this should be approved by Thursday")

---

## Data Model

```
tenants (via cross-cutting)
  └─ workflow_template (reusable approval chain definition)
       └─ workflow_template_step (ordered steps with assignee type)
  └─ document (the document being approved)
       ├─ document_version (file versions)
       └─ workflow_instance (one run of an approval chain)
            └─ workflow_step_instance (per step: status, action, comments)
  └─ approval_event (immutable audit log)
```

**Key Tables:**

```sql
-- app/V1__approvals.sql
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
```

---

## Cross-Cutting Module Mapping

| Module | How Used |
|--------|----------|
| **Multi-Tenancy** | Each company is a tenant; all documents, templates, and workflows are tenant-scoped |
| **Auth** | All approvers authenticate via Keycloak; guest approvers use time-limited email tokens |
| **RBAC** | Permissions: `documents:upload`, `documents:initiate-workflow`, `approvals:approve`, `templates:manage`. Useful for restricting who can initiate vs. who can only approve |
| **Audit** | The `approval_events` table is the primary audit trail. `@Audited` on `initiateWorkflow()`, `submitApproval()`, `rejectDocument()` for cross-cutting's business audit too |
| **Notifications** | Notify assignee: new document awaiting their approval. Notify originator: step approved, step rejected, changes requested, workflow complete. Overdue reminders |
| **Background Jobs** | Deadline monitoring job (daily: check for overdue steps, send reminders). Audit PDF generation (after workflow completes, async). Escalation job (if step overdue 5 days, escalate) |
| **File Storage** | Document files (all versions) stored in MinIO. Generated audit PDF stored in MinIO |
| **AI Gateway** | Document classification, risk flagging, approver summary, SLA prediction |
| **Export** | Export audit trail as CSV or generate PDF approval report |
| **Feature Flags** | Gate "AI features" and "guest approvers" behind paid plan |

---

## API Design

```
# Templates
GET    /api/templates                         List workflow templates
POST   /api/templates                         Create template
GET    /api/templates/{templateId}            Get template with steps
PUT    /api/templates/{templateId}            Update template
DELETE /api/templates/{templateId}            Archive template

# Documents
GET    /api/documents                         List documents (filter: status, mine, awaiting-me)
POST   /api/documents                         Upload document (with presigned URL)
GET    /api/documents/{docId}                 Get document detail
POST   /api/documents/{docId}/versions        Upload new version

# Workflows
POST   /api/documents/{docId}/workflows       Initiate workflow (from template or ad hoc)
GET    /api/workflows/{workflowId}            Get workflow status + steps
POST   /api/workflows/{workflowId}/cancel     Cancel workflow
GET    /api/workflows/{workflowId}/audit      Get full audit trail

# Approvals (step actions)
POST   /api/steps/{stepId}/approve            Approve step
POST   /api/steps/{stepId}/request-changes    Request changes (requires comment)
POST   /api/steps/{stepId}/reject             Reject (requires reason)

# My approvals inbox
GET    /api/approvals/pending                 Documents awaiting my action
GET    /api/approvals/history                 My past approval actions

# Dashboard
GET    /api/dashboard/summary                 Pending count, overdue count, completed this month

# AI
POST   /api/documents/{docId}/ai/classify     Suggest workflow template
POST   /api/documents/{docId}/ai/summarize    Generate approver summary
POST   /api/documents/{docId}/ai/flag-risks   Flag unusual clauses (PDF text extraction)
```

---

## Frontend Screens

| Screen | Purpose |
|--------|---------|
| **My Approvals Inbox** | Documents waiting for my action — primary landing page |
| **All Documents** | Full list with status badges, originator, current step |
| **New Document** | Upload file, fill metadata, choose template, set deadline |
| **Document Detail** | View file (PDF inline), workflow timeline, action buttons |
| **Workflow Builder** | Create/edit approval templates with drag-and-drop step ordering |
| **Audit Report** | Timeline view of all events; export as PDF |
| **Dashboard** | Pending, overdue, completed counts; bottleneck analysis |

---

## Monetization

| Plan | Price | Limits |
|------|-------|--------|
| **Starter** | $0 | 5 active workflows, 3 templates, 500 MB storage |
| **Business** | $59/month | Unlimited workflows + templates, 20 GB, guest approvers, PDF audit report |
| **Compliance** | $149/month | Everything + AI features, SLA analytics, delegation, escalation rules, priority support |

**Alternative:** $10/seat/month for active approvers (scales with org size).

---

## Competitive Landscape

| Competitor | Gap |
|------------|-----|
| **DocuSign** | Built for e-signature, not multi-step internal approval; $25/user/month minimum |
| **Adobe Sign** | Same as DocuSign; legal e-signature overkill for internal workflows |
| **Monday.com / Jira** | General project tools; no PDF viewer, no dedicated approval trail |
| **SharePoint** | Powerful but requires IT; poor UX; tied to Microsoft ecosystem |
| **Kissflow** | Good process tool; $15/user/month; complex for small teams |
| **Process Street** | Checklist-focused; not document-centric |

**Differentiation:** Purpose-built for document-centric approval (not just task completion), immutable audit trail with PDF export, AI document summarization so busy executives don't have to read 40-page contracts to approve them, and a clean simple UI that non-technical operations teams can configure themselves.

---

## Build Phases

### Phase 1 — MVP (9 weeks)
- Document upload (PDF, DOCX) via MinIO presigned URLs
- Workflow template builder (sequential steps, user assignees)
- Workflow initiation (from template or ad hoc)
- Review page with inline PDF viewer
- Approve / Request Changes / Reject actions with comments
- Immutable approval_events table
- Email notifications for all actions
- Deadline tracking and reminder emails (background job)
- Audit trail view
- My Approvals inbox

### Phase 2 — Growth (6 weeks)
- Parallel step routing with "any one of N" vs. "all of N" logic
- Audit PDF generation (post-workflow, background job)
- Guest approvers (email token, no login)
- Delegation (out-of-office routing)
- AI document summary for approvers

### Phase 3 — Scale
- AI risk flagging (PDF text extraction + LLM analysis)
- AI document classifier (auto-suggest template)
- Conditional routing rules
- BambooHR / Workday webhook integration
- SLA analytics dashboard

---

## Success Metrics

| Stage | Signal |
|-------|--------|
| **MVP launched** | 10 teams with ≥5 completed workflows |
| **Adoption** | Average workflow completion time under 3 days |
| **AI value** | 60%+ of approvers use the AI summary before acting |
| **Compliance signal** | 20%+ of customers mention "audit trail" as primary reason they pay |
| **Retention** | Monthly active workflows growing (new workflows initiated each month) |
