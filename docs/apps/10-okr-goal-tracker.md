# App 10: Team OKR & Goal Tracker

**Tagline:** Set ambitious objectives, track key results weekly, and align your whole team on what matters.

**Category:** Strategy / Performance Management

---

## Problem Statement

OKRs (Objectives and Key Results) are widely adopted but poorly implemented. Most teams set OKRs at the start of the quarter, document them in a Notion page or spreadsheet, and never update them again. By week 6, nobody knows what the current progress is. By the end of the quarter, scoring is a guessing game.

The specific pains:
- OKR tracking lives in Notion docs that go stale after week 2
- No visibility: "What's the company OKR score right now?" — requires someone to manually update a spreadsheet
- No weekly check-in cadence: teams skip updates because there's no structured prompt
- Cascading alignment is invisible: individual OKRs don't visibly connect to company OKRs
- Reporting to leadership is a manual exercise every quarter
- Tools like Lattice and Betterworks are $8–$15/seat and require an HR platform adoption decision

The root problem: OKRs are easy to set and hard to track. Without a tool that makes weekly check-ins fast and automatic, the practice collapses.

---

## Target Users

**Primary Buyer:** COO, VP of Operations, or CEO at a 30–300 person company

**Primary User:** Team leads and employees who own key results; executives who review company-wide progress

**Secondary User:** HR/People Ops who run the OKR cycle

**Company Profile:**
- Startups and scale-ups who've adopted OKRs but are failing at execution
- Engineering-forward companies comfortable with lightweight software (not HR suite buyers)
- Remote and hybrid teams where alignment is harder

**Willingness to Pay:** $6–$12/seat/month, or $200–$500/month flat for the whole company.

---

## Core Value Proposition

> Make OKR check-ins take 2 minutes, so they actually happen — and give leadership real-time visibility into progress without asking for it.

---

## Feature Set

### MVP (Phase 1)

**OKR Structure**
- Company-level objectives → department-level objectives → individual/team objectives (3-level hierarchy)
- Each objective has 2–5 key results
- Key result types:
  - **Metric:** Target number with current value (e.g., "MRR from $50K to $100K")
  - **Milestone:** Binary completion (e.g., "Launch feature X by March")
  - **Percentage:** Progress toward 100% (e.g., "Complete 80% of security audit")
- Objective time-bound to a cycle (Q1 2026, Q2 2026, etc.)
- Objective status: On Track / At Risk / Off Track (auto-calculated from KR progress)
- Owner assignment: each objective and KR has an owner

**Weekly Check-Ins**
- Every Monday: system sends check-in prompt to all KR owners ("Please update your key results for this week")
- Check-in page: simple — slider or numeric input for each KR, plus a mandatory confidence note ("Why did this number change?" or "Blocker: X")
- Takes under 2 minutes per KR
- Streak tracking: how many consecutive weeks has this person checked in?
- If no check-in by Tuesday noon: reminder notification

**Progress Visualization**
- Company dashboard: objective tree with color-coded health (green/yellow/red)
- Individual dashboard: my objectives and their current scores
- Weekly progress trend chart per key result (sparkline showing history)
- Team dashboard: all objectives owned by people on your team
- Quarter score summary: weighted average OKR score across all objectives

**Cascading Alignment**
- Each objective can optionally "align to" a parent objective
- Alignment tree shows how individual work connects to company goals
- Company dashboard shows this tree visually

**End-of-Quarter Review**
- System prompts objective owners to submit final score and retrospective note
- Aggregate report: what was scored, what was missed, top wins
- Export as PDF (for board/investor reporting)

### Phase 2

- OKR scoring history (previous quarters archived and browsable)
- One-on-one check-in integration: manager reviews team OKR in their 1:1 meeting view
- Public/private mode: some objectives visible company-wide, some restricted to team
- Comments on objectives (team discussion thread)
- Integration with Slack: weekly check-in reminder in DM; post company OKR update to channel
- Template library: OKR templates for common roles (Engineering Lead OKRs, Growth Team OKRs)
- Bulk KR import from CSV (for teams migrating from spreadsheets)

### AI Features

- **OKR Quality Reviewer:** When an objective is created, AI evaluates it against OKR best practices and provides specific feedback: "This key result is not measurable — it says 'improve customer satisfaction' but lacks a metric. Consider 'Increase NPS from 32 to 50'."
- **Weekly Progress Interpreter:** AI reads raw check-in data across all objectives and writes a 3-paragraph executive summary every Monday: "This week: Engineering is ahead of pace on the infrastructure migration. Sales is showing early signs of stress on the Q2 pipeline KR. Marketing's content output is on track."
- **At-Risk Early Warning:** AI monitors KR velocity (week-over-week progress rate) and flags objectives that are unlikely to hit their target based on current pace. "At current velocity, 'Revenue from new customers' will reach 65% of target, not 100%."
- **OKR Draft Generator:** User inputs their role, their team's top priority, and the company OKR they're aligned to. AI drafts a complete objective with 3 suggested key results, following best practices.
- **Retrospective Prompt:** End of quarter, AI reads the full OKR history and generates personalized reflection questions for each team ("Your team hit the delivery metric but missed the quality metric 3 quarters in a row — what patterns do you see?")

---

## Data Model

```
tenants (via cross-cutting)
  └─ okr_cycle (quarterly time period)
  └─ objective (hierarchical via parent_id)
       ├─ key_result (2-5 per objective)
       │    └─ kr_check_in (weekly progress updates)
       └─ objective_alignment (objective → parent objective)
  └─ check_in_reminder (tracks sent reminders)
```

**Key Tables:**

```sql
-- app/V1__okrs.sql
CREATE TABLE okr_cycles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    name        TEXT NOT NULL,    -- "Q2 2026"
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    status      TEXT NOT NULL DEFAULT 'planning', -- planning | active | completed
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE objectives (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    cycle_id        UUID NOT NULL REFERENCES okr_cycles(id),
    parent_id       UUID REFERENCES objectives(id),  -- null = company-level
    title           TEXT NOT NULL,
    description     TEXT,
    level           TEXT NOT NULL DEFAULT 'team',   -- company | department | team | individual
    owner_id        UUID REFERENCES cc.users(id),
    department      TEXT,
    status          TEXT NOT NULL DEFAULT 'on_track', -- on_track | at_risk | off_track
    progress        NUMERIC(5,2) NOT NULL DEFAULT 0,  -- 0-100% (calculated from KRs)
    final_score     NUMERIC(3,2),   -- 0.0-1.0, set at quarter end
    final_note      TEXT,
    is_public       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE key_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    objective_id    UUID NOT NULL REFERENCES objectives(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    title           TEXT NOT NULL,
    description     TEXT,
    kr_type         TEXT NOT NULL DEFAULT 'metric',  -- metric | milestone | percentage
    owner_id        UUID REFERENCES cc.users(id),
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

CREATE TABLE kr_check_ins (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_result_id   UUID NOT NULL REFERENCES key_results(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    owner_id        UUID REFERENCES cc.users(id),
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

CREATE TABLE check_in_reminders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    week_start      DATE NOT NULL,
    user_id         UUID REFERENCES cc.users(id),
    sent_at         TIMESTAMPTZ,
    reminder_sent_at TIMESTAMPTZ,   -- Tuesday noon follow-up
    checked_in_at   TIMESTAMPTZ
);
```

---

## Cross-Cutting Module Mapping

| Module | How Used |
|--------|----------|
| **Multi-Tenancy** | Each company is a tenant; all OKR data (cycles, objectives, KRs, check-ins) are tenant-scoped |
| **Auth** | All users authenticate via Keycloak |
| **RBAC** | Permissions: `objectives:create`, `objectives:view_all`, `okr:admin` (manage cycles, set company objectives). Role: `viewer` (see all objectives, no edit), `contributor` (edit own), `admin` (create cycles, edit all) |
| **Audit** | `@Audited` on `publishObjective()`, `setFinalScore()`, `deleteCycle()` |
| **Notifications** | Weekly check-in reminder (Monday). Overdue follow-up (Tuesday). Owner notified when objective marked At Risk. Team lead notified when company OKR falls to Off Track |
| **Background Jobs** | Monday check-in reminder job (send reminders per user). Tuesday follow-up job (no check-in yet). Weekly executive summary generation (AI). OKR status recalculation on each check-in (update objective.progress from KR averages). Quarter-end reminder |
| **AI Gateway** | OKR quality review, weekly progress summary, at-risk early warning, OKR draft generator, retrospective prompt |
| **Feature Flags** | Gate "AI features" and "Slack integration" behind paid plan |
| **Export** | Export quarter summary as PDF. Export all OKR data as CSV |
| **Webhooks** | Events: `objective.at_risk`, `cycle.completed` — trigger Slack notifications or external dashboards |

---

## OKR Progress Calculation Logic

```
Key Result Progress:
  metric KR:     progress = (current - start) / (target - start) * 100
  milestone KR:  progress = is_completed ? 100 : 0
  percentage KR: progress = current_value (already a %)

Objective Progress:
  average of all owned KR progress values

Objective Status:
  progress >= 70%:  on_track (green)
  40% <= progress < 70%: at_risk (yellow)
  progress < 40%:   off_track (red)

Recalculation trigger:
  After every kr_check_in INSERT → background job updates key_result.progress
  → then updates objective.progress + status
  → then fan-out to update parent objectives (up the hierarchy)
```

---

## API Design

```
# Cycles
GET    /api/cycles                            List cycles
POST   /api/cycles                            Create cycle
GET    /api/cycles/{cycleId}                  Get cycle with objectives
PUT    /api/cycles/{cycleId}                  Update cycle
POST   /api/cycles/{cycleId}/activate         Activate cycle (starts reminders)

# Objectives
GET    /api/cycles/{cycleId}/objectives       List objectives (tree or flat)
POST   /api/cycles/{cycleId}/objectives       Create objective
GET    /api/objectives/{objectiveId}          Get objective with KRs
PUT    /api/objectives/{objectiveId}          Update objective
DELETE /api/objectives/{objectiveId}          Delete objective
POST   /api/objectives/{objectiveId}/score    Submit final quarter score

# Key Results
GET    /api/objectives/{objectiveId}/krs      List KRs
POST   /api/objectives/{objectiveId}/krs      Create KR
PUT    /api/krs/{krId}                        Update KR
DELETE /api/krs/{krId}                        Delete KR

# Check-ins
GET    /api/krs/{krId}/check-ins              Check-in history
POST   /api/krs/{krId}/check-ins              Submit weekly check-in

# My check-in (weekly prompt)
GET    /api/check-in/pending                  All my KRs needing check-in this week
POST   /api/check-in/bulk                     Bulk check-in for all my KRs (single form)

# Dashboard
GET    /api/dashboard/company                 Company OKR tree with health
GET    /api/dashboard/my                      My objectives + progress
GET    /api/dashboard/team/{userId}           Team's objectives

# AI
POST   /api/objectives/{objectiveId}/ai/review   Quality review
POST   /api/objectives/ai/draft                  Draft OKR from role + priority input
POST   /api/krs/{krId}/ai/check-velocity         At-risk velocity warning
GET    /api/cycles/{cycleId}/ai/weekly-summary   Executive summary for this week
POST   /api/cycles/{cycleId}/ai/retro-prompts    Generate retrospective questions
```

---

## Frontend Screens

| Screen | Purpose |
|--------|---------|
| **Company Dashboard** | Visual OKR tree (hierarchy), health heatmap, drill-down |
| **My OKRs** | Personal objective list with KR progress bars |
| **Weekly Check-In** | Quick update form: all my KRs in one page, slider/input + note |
| **Objective Detail** | KR list, check-in history chart, alignment visualization |
| **OKR Builder** | Create objective → add KRs → assign owner → align to parent |
| **Team View** | Manager sees all team members' OKRs aggregated |
| **Quarter Report** | End-of-quarter scores, wins, misses, export to PDF |
| **Cycle Settings** | Manage cycles, set check-in day, configure reminders |

---

## Monetization

| Plan | Price | Limits |
|------|-------|--------|
| **Starter** | $0 | 10 users, 1 active cycle, no AI features |
| **Team** | $7/seat/month | Unlimited users + cycles, Slack reminders, PDF export, webhooks |
| **Business** | $12/seat/month | Everything + AI quality review, weekly AI summary, at-risk warnings, retrospective prompts |

**Alternative:** $299/month flat for up to 50 employees (simpler for SMBs who don't think per-seat).

---

## Competitive Landscape

| Competitor | Gap |
|------------|-----|
| **Lattice** | Full performance platform; $11/seat/month minimum; includes 1:1s, reviews — overkill for just OKRs |
| **Betterworks** | Enterprise-only; $15+/seat; requires HR admin buy-in |
| **Ally.io (Microsoft Viva Goals)** | Microsoft ecosystem lock-in; $6/seat as part of M365 bundle |
| **Weekdone** | OKR-focused; $10.75/seat; dated UI; no AI features |
| **Perdoo** | $9.99/seat; good OKR tool; no AI features |
| **Notion / spreadsheets** | No automation; check-ins don't happen; no visualization |

**Differentiation:** Weekly check-in in under 2 minutes (no excuses not to update), AI at-risk early warning that flags problems before the quarter is over, and a company OKR tree that gives leadership instant visibility without any manual report preparation.

---

## Build Phases

### Phase 1 — MVP (8 weeks)
- Cycle management (create, activate, close)
- Objective hierarchy (company → department → team)
- Key results (metric, milestone, percentage types)
- Weekly check-in form (per user, for all their KRs)
- Progress calculation on every check-in (background job)
- Company and personal dashboards
- Monday check-in reminder emails
- Tuesday follow-up for missing check-ins

### Phase 2 — Growth (5 weeks)
- OKR alignment tree visualization
- Manager/team view
- Quarter-end scoring and retrospective note
- PDF quarter report export
- Streak tracking
- Slack reminder integration

### Phase 3 — Scale
- AI OKR quality reviewer
- AI weekly executive summary
- AI at-risk early warning
- AI OKR draft generator
- AI retrospective prompts
- CSV import from spreadsheets

---

## Success Metrics

| Stage | Signal |
|-------|--------|
| **MVP launched** | 20 teams with an active cycle and ≥3 objectives each |
| **Check-in adoption** | 70%+ of KR owners submit at least 3 of 4 weekly check-ins per month |
| **Retention** | Teams renew for the next quarter (cycle-based renewal pattern) |
| **AI value** | 50%+ of new objectives use AI quality review and act on the feedback |
| **Executive value** | Team leads report weekly summary replacing a manual status meeting |
| **Monetization** | 30% free-to-paid conversion (the free tier limits force upgrade when teams grow past 10) |
