# MISSION
You are an autonomous principal engineer. Your mission is to implement the assigned application as a new module in this monorepo.

# ARCHITECTURE RULES
1. **Module Location**: Create the app under apps/06-employee-onboarding-orchestrator.
2. **Dependency**: Your pom.xml MUST have saas-os-parent as the parent and saas-os-core as a dependency.
3. **Core Reuse**: Use classes from com.changelog.* in saas-os-core for Billing, AI, and Multi-tenancy.
4. **Java Standard**: Use Java 21 only.

# SPECIFICATION
# App 06: Employee Onboarding Orchestrator

**Tagline:** Structured onboarding plans that run themselves — every new hire gets the same great first week.

**Category:** HR / People Operations

---

## Problem Statement

The first week at a new job is overwhelming or underwhelming — rarely right. For the company, onboarding is an ops problem: someone has to manually send the same emails, set up accounts, assign reading, schedule meetings, and track that everything got done. This is unscalable busywork that falls apart when the person managing onboarding is busy or when the company hires 5 people in one month.

The specific pains:
- No consistent process: employee experience depends entirely on who's managing their onboarding
- Onboarding tasks fall through the cracks (IT setup not done, policies not signed)
- New hire doesn't know what to do in the first week — confusion leads to a bad impression
- HR manager spends 4+ hours per new hire just doing checklist coordination
- There's no visibility: who finished reading the handbook? Who still hasn't set up 2FA?
- Offboarding is even more chaotic (revoking access, returning equipment)

The compounding cost: poor onboarding causes 20% higher first-year attrition — expensive for any company.

---

## Target Users

**Primary Buyer:** HR Manager / People Ops Lead / COO at a 30–500 person company

**Primary User:** HR manager setting up onboarding plans; new hire completing tasks

**Secondary User:** IT, manager, buddy — people assigned specific onboarding tasks

**Company Profile:**
- Fast-growing startups and scale-ups (hiring 2–20 people per month)
- Remote-first companies (async onboarding is critical)
- Companies without a full HRIS (not on Workday/BambooHR)

**Willingness to Pay:** $6–$12/seat/month for all employees, or $100–$300/month flat for small HR teams.

---

## Core Value Proposition

> Build your perfect onboarding plan once. Every new hire gets an automatically orchestrated, consistent first week — with nothing forgotten and full visibility for HR.

---

## Feature Set

### MVP (Phase 1)

**Onboarding Plan Templates**
- Create named templates (e.g., "Software Engineer Onboarding", "Marketing Hire Onboarding")
- Templates contain ordered tasks with:
  - Task name and description
  - Assignee type: `new_hire` / `hr` / `it` / `manager` / `buddy`
  - Due date logic: "Day 1", "Day 3", "Week 2", "Before start date"
  - Task type: `complete` (checkbox), `read` (document/link), `submit_form` (freetext or file upload), `schedule_meeting` (link to calendar)
  - Optional: attach a document or resource link to any task

**Onboarding Instances**
- Start an onboarding: select template + assign to a new hire (name, email, start date, department)
- System resolves all task due dates based on start date
- Sends welcome email to new hire with their personal onboarding portal link (no login required — magic link)

**New Hire Portal**
- Personal checklist view: tasks grouped by day/week
- Progress bar showing % complete
- Each task: description, due date, resources, and a Complete button
- For "submit_form" tasks: text input or file upload directly in the portal
- Simple, welcoming UI (not a corporate dashboard)

**HR Dashboard**
- All active onboardings with progress %
- Overdue tasks highlighted (task not completed by due date)
- Upcoming tasks in the next 3 days
- "Not started" hires (portal not opened yet)
- Per-hire drill-down: full task status, who's blocked

**Notifications & Reminders**
- Day-of task reminders sent to the assignee
- Overdue reminder if task not completed within 24 hours of due date
- HR notified: "Hire X has not opened their portal" (Day 2 trigger)
- HR daily digest: onboarding health across all active hires

**Offboarding Templates**
- Same template system applies to offboarding
- Tasks: revoke access, return equipment, exit survey, conduct exit interview, archive accounts

### Phase 2

- Integration with calendar (Google Calendar / Outlook) to auto-schedule meetings in "schedule_meeting" tasks
- Manager tasks: auto-assign 1:1 meeting, assign mentor, schedule 30/60/90 check-ins
- Pre-boarding tasks (before start date): send paperwork, laptop shipping, equipment request
- Custom fields per hire (home address for equipment, GitHub username for IT provisioning)
- Onboarding analytics: average time to completion per template, most frequently skipped tasks
- Bulk start (onboard 10 hires from a CSV upload)
- Integration with Slack: new hire joins a `#welcome` channel automatically; buddy gets a Slack DM

### AI Features

- **Onboarding Plan Generator:** Input: job title, department, seniority. AI drafts a complete onboarding plan template as a starting point ("Here's a 30-day onboarding plan for a Senior Backend Engineer — 24 tasks across IT setup, product knowledge, team meetings, and first deliverables")
- **Task Description Writer:** HR types a task name ("Read Security Policy"); AI writes a clear, friendly task description with context ("Review our security policy to understand how we handle data, password requirements, and acceptable use. Takes about 15 minutes.")
- **First-Week Summary:** AI generates a friendly 5-sentence "Here's what your first week looks like" summary sent with the welcome email, written in the company's voice
- **Completion Anomaly Detection:** AI flags when a new hire's completion rate falls significantly below average ("Alex is 40% behind peer completion rate at Day 5 — consider reaching out")

---

## Data Model

```
tenants (via cross-cutting)
  └─ onboarding_template (reusable plan definition)
       └─ template_task (ordered tasks with assignee type and due-day logic)
  └─ onboarding_instance (one hire's active onboarding)
       ├─ task_instance (per task: assignee, status, due_date)
       │    └─ task_submission (form answers or uploaded files)
       └─ onboarding_event (immutable log of all actions)
```

**Key Tables:**

```sql
-- app/V1__onboarding.sql
CREATE TABLE onboarding_templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    name        TEXT NOT NULL,
    description TEXT,
    category    TEXT NOT NULL DEFAULT 'onboarding', -- onboarding | offboarding
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_by  UUID REFERENCES cc.users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE template_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES onboarding_templates(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    description     TEXT,
    task_type       TEXT NOT NULL DEFAULT 'complete',
    -- complete | read | submit_form | schedule_meeting
    assignee_type   TEXT NOT NULL DEFAULT 'new_hire',
    -- new_hire | hr | it | manager | buddy
    due_day_offset  INT NOT NULL DEFAULT 1,   -- days from start_date (negative = before)
    resource_url    TEXT,
    resource_name   TEXT,
    is_required     BOOLEAN NOT NULL DEFAULT true,
    position        INT NOT NULL DEFAULT 0
);

CREATE TABLE onboarding_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    template_id     UUID REFERENCES onboarding_templates(id),
    hire_name       TEXT NOT NULL,
    hire_email      TEXT NOT NULL,
    hire_role       TEXT,
    department      TEXT,
    manager_id      UUID REFERENCES cc.users(id),
    buddy_id        UUID REFERENCES cc.users(id),
    start_date      DATE NOT NULL,
    status          TEXT NOT NULL DEFAULT 'active', -- active | completed | cancelled
    portal_token    TEXT NOT NULL UNIQUE,  -- magic link token
    portal_opened_at TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_by      UUID REFERENCES cc.users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE task_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    onboarding_id   UUID NOT NULL REFERENCES onboarding_instances(id) ON DELETE CASCADE,
    template_task_id UUID REFERENCES template_tasks(id),
    title           TEXT NOT NULL,
    description     TEXT,
    task_type       TEXT NOT NULL,
    assignee_id     UUID REFERENCES cc.users(id),   -- resolved from type
    assignee_email  TEXT,
    due_date        DATE NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pending', -- pending | completed | skipped
    completed_at    TIMESTAMPTZ,
    completed_by    TEXT  -- new hire email or user id
);

CREATE TABLE task_submissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_instance_id UUID NOT NULL REFERENCES task_instances(id),
    response_text   TEXT,
    file_key        TEXT,    -- MinIO object key for uploaded files
    file_name       TEXT,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## Cross-Cutting Module Mapping

| Module | How Used |
|--------|----------|
| **Multi-Tenancy** | Each company is a tenant; all templates and instances are tenant-scoped |
| **Auth** | HR staff use Keycloak; new hires access their portal via magic link token (no account) |
| **RBAC** | Permissions: `templates:manage`, `onboarding:start`, `onboarding:view_all`. Role: `hr_admin` (full access), `manager` (view own team's hires only) |
| **Audit** | `@Audited` on `startOnboarding()`, `completeTask()`, `cancelOnboarding()` |
| **Notifications** | Task due-day reminders to assignees (new hire + IT + HR). Overdue alerts to HR. Welcome email to new hire. Daily digest to HR manager |
| **Background Jobs** | Daily job: find tasks due today → send day-of reminders. Daily job: find overdue tasks → send overdue alerts. Daily: find portals not opened after Day 2 → alert HR. Scheduled per-instance: trigger completion check when all tasks done |
| **File Storage** | New hire document uploads (form submissions) stored in MinIO, tenant-isolated |
| **AI Gateway** | Plan generator, task description writer, first-week summary, anomaly detection |
| **Feature Flags** | Gate "AI plan generator" and "bulk start" behind paid plan |
| **Export** | Export onboarding completion report as CSV |

---

## API Design

```
# Templates
GET    /api/templates                         List templates
POST   /api/templates                         Create template
GET    /api/templates/{templateId}            Get template with tasks
PUT    /api/templates/{templateId}            Update template
DELETE /api/templates/{templateId}            Archive template
PUT    /api/templates/{templateId}/tasks      Replace all tasks (bulk update)

# Onboarding instances
GET    /api/onboardings                       List active onboardings (HR view)
POST   /api/onboardings                       Start new onboarding
GET    /api/onboardings/{onboardingId}        Get instance with task status
POST   /api/onboardings/{onboardingId}/resend-welcome  Resend welcome email
POST   /api/onboardings/{onboardingId}/cancel  Cancel onboarding

# Task actions (HR)
GET    /api/onboardings/{onboardingId}/tasks  List tasks with status
POST   /api/tasks/{taskId}/complete           Mark task complete (HR-side tasks)
POST   /api/tasks/{taskId}/skip               Skip task with reason

# New hire portal (magic link authenticated)
GET    /portal/{token}                        New hire portal (task list)
POST   /portal/{token}/tasks/{taskId}/complete  Complete task
POST   /portal/{token}/tasks/{taskId}/submit  Submit form response or file upload

# Dashboard
GET    /api/dashboard/onboarding-health       Active hires, completion rates, overdue count

# AI
POST   /api/ai/generate-plan                  Generate template from job title + department
POST   /api/templates/{templateId}/ai/write-descriptions  Rewrite all task descriptions
GET    /api/onboardings/{onboardingId}/ai/health-check  Anomaly detection for this hire
```

---

## Frontend Screens

| Screen | Audience | Purpose |
|--------|----------|---------|
| **HR Dashboard** | HR | All active onboardings, health at a glance, overdue tasks |
| **Onboarding Detail** | HR | Full task list for one hire, progress, drill-down |
| **Start Onboarding** | HR | Select template, fill hire details, set start date |
| **Template Builder** | HR | Create/edit task list with drag-and-drop reordering |
| **New Hire Portal** | New hire | Personal task list, progress bar, due dates, complete actions |
| **Task Complete View** | New hire | Individual task with description, resource link, submit button |
| **Analytics** | HR | Time-to-completion trends, task skip rates, template effectiveness |

---

## Monetization

| Plan | Price | Limits |
|------|-------|--------|
| **Starter** | $0 | 1 active onboarding at a time, 1 template |
| **Growth** | $8/seat/month (active employees) | Unlimited simultaneous onboardings, 10 templates, offboarding |
| **Scale** | $149/month flat | Unlimited everything + AI features, bulk start, analytics, priority support |

**Alternative Pricing:** $25 per completed onboarding (pay-as-you-go) — good for companies that hire infrequently.

---

## Competitive Landscape

| Competitor | Gap |
|------------|-----|
| **BambooHR** | Full HRIS suite; $6–$9/seat for the whole platform; too heavy if you only need onboarding |
| **Sapling (Kallidus)** | Strong onboarding tool but enterprise-priced ($15/seat/month minimum) |
| **Notion/ClickUp** | Manual checklists; no automation, no tracking, no notifications |
| **WorkBright** | Document-focused (I-9, W-4); not a workflow orchestrator |
| **Enboarder** | Expensive enterprise tool; overkill for sub-500 companies |

**Differentiation:** Magic-link new hire portal (zero friction — new hires don't create an account), AI plan generator that writes a complete onboarding in 30 seconds, and beautiful UX that makes a good first impression — since the new hire's first interaction with company tools is this portal.

---

## Build Phases

### Phase 1 — MVP (8 weeks)
- Template builder (tasks with type, assignee type, due-day offset)
- Start onboarding (magic link generation, welcome email)
- New hire portal (task list, complete actions, file uploads)
- HR dashboard (active onboardings, progress, overdue)
- Day-of task reminder emails (background job)
- Overdue task alerts (background job)
- Portal-not-opened alert (Day 2 trigger)

### Phase 2 — Growth (5 weeks)
- Offboarding template support
- Manager and buddy task assignment resolution
- Onboarding analytics (time-to-completion, skip rates)
- Bulk start from CSV
- AI plan generator
- AI task description writer

### Phase 3 — Scale
- Google Calendar / Outlook integration for meeting tasks
- Slack integration (auto-join channels, buddy DM)
- Pre-boarding tasks (before start date)
- Custom fields per hire
- AI anomaly detection

---

## Success Metrics

| Stage | Signal |
|-------|--------|
| **MVP launched** | 15 HR teams with ≥1 active template |
| **Adoption** | 70%+ of new hire portals opened within Day 1 |
| **Task completion** | Average task completion rate above 85% per onboarding |
| **Time saved** | HR reports ≥2 hours saved per hire |
| **Monetization** | 25% of active teams upgrade by month 3 |
| **Retention** | Monthly onboarding instances growing (hiring is a leading indicator) |
