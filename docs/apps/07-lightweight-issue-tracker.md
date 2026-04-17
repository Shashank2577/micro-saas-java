# App 07: Lightweight Issue Tracker

**Tagline:** Report bugs, track them to resolution, get notified when it's done — without the Jira overhead.

**Category:** Engineering / Product

---

## Problem Statement

Jira is the industry standard but it is notoriously slow, overwhelmingly complex, and deeply unpopular with developers. Small and mid-sized engineering teams don't need epics, story points, sprints, velocity charts, or a 40-tab settings panel. They need: a place to report bugs, assign them to someone, track their status, and get notified when they're resolved.

The specific pains:
- Jira's 200ms-per-click UX kills productivity; developers avoid it
- Setting up Jira for a new project takes hours (schemes, workflows, permissions)
- Non-engineers (QA, customer support, product) struggle to use it
- Linear is closer to what developers want but has its own learning curve and $8/seat cost
- GitHub Issues works for open source but lacks multi-project management, SLA tracking, and customer-facing features
- Customer support teams can't submit bugs directly to the engineering backlog without a separate process

The market segment: teams of 3–30 engineers who want fast, minimal, modern bug tracking without ceremony.

---

## Target Users

**Primary Buyer:** Engineering lead, CTO, or Head of Engineering at a startup or scale-up

**Primary User:** Developers (create, update, resolve issues), QA engineers (file bugs), customer support (escalate customer bugs)

**Secondary User:** Product manager (triage and prioritize), customers (submit bug reports via embedded widget)

**Company Profile:**
- 5–100 person engineering teams
- SaaS companies where bugs directly affect customer retention
- Agencies managing bugs across multiple client projects
- Any team that tried Jira and gave up

**Willingness to Pay:** $8–$20/seat/month, or $50–$150/month flat for small teams.

---

## Core Value Proposition

> A bug tracker that developers actually use — fast, minimal, and hooked up to Slack and GitHub with zero configuration.

---

## Feature Set

### MVP (Phase 1)

**Projects**
- Named projects (one per product or repository)
- Members per project (who can view/edit issues in this project)
- Default labels per project (Bug, Feature Request, Improvement, Question)

**Issues**
- Create issue: title, description (markdown), project, priority (P0–P3), assignee, labels
- Issue status: `Open` → `In Progress` → `Resolved` → `Closed`
- Reporter tracked (who created the issue)
- Due date (optional)
- File attachments (screenshots, log files) — direct upload to storage
- Linked issues (blocks / is blocked by / related to)
- Sub-tasks (checklist items within an issue)

**Comments & Activity**
- Threaded comments with markdown support
- @ mention a team member (sends notification)
- Activity log: status changes, reassignments, label changes
- Code block support in comments (for stack traces)

**Views & Filters**
- Board view (kanban by status)
- List view with sortable columns
- Filter: project, assignee, label, priority, date range, "assigned to me"
- Saved filters ("My Open P0 Bugs", "Unassigned Issues")

**Notifications**
- Assigned to you: immediate notification (in-app + email)
- Someone comments on your issue: notification
- Issue you reported is resolved: notification
- @ mention: immediate notification
- Daily digest of your open issues

### Phase 2

- SLA tracking: P0 must be resolved in 4 hours, P1 in 24 hours — auto-escalate
- Customer-facing bug report widget (embed in your product — customer submits, it becomes an issue)
- GitHub integration: link issues to GitHub PRs (issue auto-resolves when PR merges)
- Slack integration: post to channel when P0/P1 created; resolve from Slack emoji reaction
- Sprint planning view (lightweight — group issues into named cycles)
- Time tracking per issue (log hours spent)
- Issue templates (pre-fill common bug reports: "Server error", "Payment failure", etc.)
- Bulk operations (close all, reassign all, label all)

### AI Features

- **Duplicate Detection:** When a new issue is created, vector similarity search flags potentially duplicate issues ("This looks similar to #142 — check before creating a new issue")
- **Priority Suggester:** AI reads the issue description and suggests a priority level with reasoning ("P1 — this appears to be a payment flow failure affecting user checkout, which typically warrants high priority")
- **Repro Steps Formatter:** User pastes a messy bug description; AI reformats it into structured steps: Environment, Steps to Reproduce, Expected Result, Actual Result
- **Root Cause Hypothesis:** For issues with stack traces pasted in, AI reads the trace and suggests the most likely root cause ("NullPointerException at line 234 suggests that user.getProfile() is returning null — check whether profile is optional in the User entity")
- **Release Notes Generator:** At the end of a sprint/cycle, AI generates a changelog from resolved issues ("Fixed: payment form validation error, Improved: search performance on large datasets, Resolved: mobile nav menu overlap")

---

## Data Model

```
tenants (via cross-cutting)
  └─ project (one per product/repo)
       ├─ project_member (junction: project ↔ user, role)
       └─ issue
            ├─ issue_comment
            ├─ issue_attachment (files)
            ├─ issue_label (many-to-many)
            ├─ issue_link (blocks/related relationships)
            └─ issue_event (immutable activity log)
  └─ label (tenant-wide label definitions)
```

**Key Tables:**

```sql
-- app/V1__issues.sql
CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    name        TEXT NOT NULL,
    slug        TEXT NOT NULL,
    description TEXT,
    status      TEXT NOT NULL DEFAULT 'active',
    created_by  UUID REFERENCES cc.users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);

CREATE TABLE labels (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    name        TEXT NOT NULL,
    color       TEXT NOT NULL DEFAULT '#6B7280',
    UNIQUE(tenant_id, name)
);

CREATE TABLE issues (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    number          INT NOT NULL,  -- project-scoped sequential number (#1, #2, ...)
    title           TEXT NOT NULL,
    description     TEXT,
    content_tsv     tsvector GENERATED ALWAYS AS
                    (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,''))) STORED,
    status          TEXT NOT NULL DEFAULT 'open',
    -- open | in_progress | resolved | closed
    priority        TEXT NOT NULL DEFAULT 'p2',  -- p0 | p1 | p2 | p3
    reporter_id     UUID REFERENCES cc.users(id),
    assignee_id     UUID REFERENCES cc.users(id),
    due_date        DATE,
    resolved_at     TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    embedding       vector(1536),   -- pgvector for duplicate detection
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(project_id, number)
);

CREATE INDEX issues_tsv_idx ON issues USING GIN(content_tsv);

CREATE TABLE issue_label_assignments (
    issue_id    UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    label_id    UUID NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY (issue_id, label_id)
);

CREATE TABLE issue_comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id    UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    author_id   UUID REFERENCES cc.users(id),
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE issue_attachments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id    UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    file_key    TEXT NOT NULL,
    file_name   TEXT NOT NULL,
    file_size_bytes BIGINT,
    uploaded_by UUID REFERENCES cc.users(id),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE issue_links (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_issue_id UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    target_issue_id UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    link_type       TEXT NOT NULL  -- blocks | is_blocked_by | related_to | duplicates
);

-- Immutable activity log
CREATE TABLE issue_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id    UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    actor_id    UUID REFERENCES cc.users(id),
    event_type  TEXT NOT NULL,
    -- created | status_changed | assigned | commented | label_added | label_removed
    -- priority_changed | attachment_added | linked | resolved
    old_value   TEXT,
    new_value   TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## Cross-Cutting Module Mapping

| Module | How Used |
|--------|----------|
| **Multi-Tenancy** | Each team/company is a tenant; projects, issues, and labels are tenant-scoped |
| **Auth** | Team members authenticate via Keycloak; customers use embedded widget (email-based, no account) |
| **RBAC** | Permissions: `projects:manage`, `issues:create`, `issues:resolve`, `issues:delete`. Role: `reporter` (can only file issues, not resolve) |
| **Audit** | `issue_events` table is the primary activity log. `@Audited` on `resolveIssue()`, `deleteIssue()` |
| **Notifications** | Assignment, comment mention, status change, resolution — all via notification service. P0/P1 creation → immediate alert |
| **Webhooks** | Events: `issue.created`, `issue.resolved`, `issue.assigned` — consumed by Slack integration or CI/CD pipelines |
| **File Storage** | Issue attachments (screenshots, log files) stored in MinIO with presigned upload URLs |
| **Background Jobs** | SLA monitoring job (hourly: check P0/P1 issues past SLA → escalation notification). Weekly digest email to users |
| **Search** | Full-text search via `content_tsv`. Vector search via `embedding` for duplicate detection |
| **AI Gateway** | Duplicate detection, priority suggestion, repro steps formatter, root cause hypothesis, release notes generator |
| **Feature Flags** | Gate "SLA tracking", "customer widget", and "AI features" behind paid plan |
| **Export** | Export issues as CSV (filter by project, date range, status) |

---

## API Design

```
# Projects
GET    /api/projects                          List projects
POST   /api/projects                          Create project
GET    /api/projects/{projectId}              Get project
PUT    /api/projects/{projectId}              Update project
DELETE /api/projects/{projectId}              Archive project
GET    /api/projects/{projectId}/members      List members
POST   /api/projects/{projectId}/members      Add member

# Issues
GET    /api/projects/{projectId}/issues       List issues (filter: status, assignee, priority, label)
POST   /api/projects/{projectId}/issues       Create issue
GET    /api/issues/{issueId}                  Get issue
PUT    /api/issues/{issueId}                  Update issue (status, assignee, priority, title)
DELETE /api/issues/{issueId}                  Delete issue
GET    /api/issues/{issueId}/activity         Activity log

# Comments
GET    /api/issues/{issueId}/comments         List comments
POST   /api/issues/{issueId}/comments         Add comment
PUT    /api/comments/{commentId}              Edit comment
DELETE /api/comments/{commentId}              Delete comment

# Attachments
POST   /api/issues/{issueId}/attachments      Get presigned upload URL
GET    /api/issues/{issueId}/attachments      List attachments

# Labels
GET    /api/labels                            List labels
POST   /api/labels                            Create label
DELETE /api/labels/{labelId}                  Delete label

# Search
GET    /api/issues/search?q=query             Full-text issue search

# AI
POST   /api/issues/ai/check-duplicate         Check new issue for duplicates (before create)
POST   /api/issues/{issueId}/ai/suggest-priority  Suggest priority from description
POST   /api/issues/{issueId}/ai/format-repro  Format into structured repro steps
POST   /api/issues/{issueId}/ai/root-cause    Analyze stack trace for root cause

# Customer widget (public, no auth)
POST   /widget/{tenantSlug}/{projectSlug}/report  Submit bug from embedded widget
```

---

## Frontend Screens

| Screen | Purpose |
|--------|---------|
| **My Issues** | Issues assigned to me, sorted by priority — daily work view |
| **Board View** | Kanban by status (Open / In Progress / Resolved / Closed) |
| **List View** | Filterable, sortable table of issues |
| **Issue Detail** | Full issue with description, activity log, comments, attachments, AI tools |
| **New Issue** | Create form with AI priority suggestion and duplicate check |
| **Project Settings** | Members, labels, SLA config, webhook endpoints |
| **Search** | Full-text + semantic search across all issues |

---

## Monetization

| Plan | Price | Limits |
|------|-------|--------|
| **Free** | $0 | 1 project, 3 users, no integrations |
| **Team** | $10/seat/month | Unlimited projects, webhooks, CSV export, file attachments |
| **Pro** | $15/seat/month | Everything + AI features, SLA tracking, customer widget, GitHub integration |

**Team cap pricing alternative:** $49/month for up to 5 seats, $99/month for up to 15 seats — simpler for small teams.

---

## Competitive Landscape

| Competitor | Gap |
|------------|-----|
| **Jira** | Overwhelming complexity; slow; hated by developers |
| **Linear** | Closest competitor; excellent UX; $8/seat; no AI features; no customer widget |
| **GitHub Issues** | Free; no project management across repos; no notifications system |
| **Shortcut (Clubhouse)** | $8.50/seat; good but no AI; no customer-facing widget |
| **Plane** | Open source Jira alternative; good but no AI; complex setup |

**Differentiation:** Speed (sub-100ms interactions, unlike Jira), AI root cause analysis from stack traces (developer-specific value), customer-facing bug report widget, and clean kanban + list views without any sprint/velocity ceremony.

---

## Build Phases

### Phase 1 — MVP (7 weeks)
- Project + member management
- Issue CRUD (title, description, status, priority, assignee, labels)
- Board view (kanban) and list view
- Comments with markdown
- File attachments via MinIO presigned URLs
- Activity log (immutable issue_events)
- Notifications (assignment, comment, resolution)
- Full-text search (PostgreSQL tsvector)

### Phase 2 — Growth (5 weeks)
- pgvector embeddings for duplicate detection
- AI priority suggester at issue creation
- AI repro steps formatter
- Webhook endpoints for Slack/GitHub integration
- Linked issues (blocks/related)
- CSV export

### Phase 3 — Scale
- AI root cause analysis (stack trace parsing)
- AI release notes generator per cycle
- SLA monitoring and escalation
- Customer bug report widget
- GitHub PR linking (auto-resolve on merge)
- Sprint/cycle planning view

---

## Success Metrics

| Stage | Signal |
|-------|--------|
| **MVP launched** | 20 engineering teams with ≥5 issues created |
| **Daily use** | 60%+ of team members create or update an issue at least 3x/week |
| **AI adoption** | 40%+ of new P0/P1 issues use the repro steps formatter |
| **Monetization** | 30% free-to-paid conversion at 60 days |
| **Retention** | Teams have ≥10 issues created per month (active tracking signal) |
