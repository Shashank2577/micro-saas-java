# App 02: Team Feedback & Roadmap

**Tagline:** Collect feature requests, let users vote, publish a public roadmap — all in one place.

**Category:** Product Management

---

## Problem Statement

Product teams at SaaS companies drown in feature requests that arrive through email, support tickets, Slack messages, and sales calls. There is no single source of truth for what customers want, no visibility into which requests are most popular, and no easy way to communicate back to customers when something they asked for is built.

The specific pains:
- Duplicate feature requests across channels (the same ask from 20 customers, unlinked)
- No way to prioritize by customer demand vs. gut feel
- Customers email "is X feature coming?" — no self-serve answer
- When a feature ships, there's no automated way to notify everyone who asked for it
- Internal roadmap is a private Notion doc that doesn't align with public promises

This problem compounds as a company grows past 50 customers. It's a retention and roadmap alignment problem.

---

## Target Users

**Primary Buyer:** Head of Product / VP Product / Founder-as-PM at a B2B SaaS company

**Primary User:** Product manager updating the roadmap; support team triaging requests

**End User (submitter):** Customers and internal stakeholders submitting feedback

**Company Profile:**
- B2B SaaS companies, 10–500 customers
- Any team that ships software to external users
- Internal tools teams managing an engineering backlog visible to stakeholders

**Willingness to Pay:** $30–$150/month depending on team size and number of boards.

---

## Core Value Proposition

> Turn scattered feedback into a prioritized, public-facing roadmap. Automatically notify everyone who upvoted when their feature ships.

---

## Feature Set

### MVP (Phase 1)

**Feedback Boards**
- Create named boards per product area (e.g., "Mobile App", "Integrations", "Dashboard")
- Each board has a public URL for customers to submit and vote
- Boards can be public (anyone) or private (team only)
- Widget embed code — drop a feedback button into your own app

**Feedback Posts**
- Customers submit posts: title + description
- Agency (team) can submit on behalf of a customer ("I heard this from Acme Corp")
- Auto-deduplication suggestion: when submitting, AI finds similar existing posts and asks "is this the same as X?"
- Upvoting — one vote per email address per post
- Voters list (admin view: who voted for this?)
- Comments on posts (customer ↔ PM dialogue)
- Status labels: `Under Review`, `Planned`, `In Progress`, `Completed`, `Declined`

**Roadmap View**
- Public roadmap grouped by status columns (kanban-style)
- Can hide individual posts from public view while keeping them internally visible
- ETA field on roadmap items (optional, shows "Q3 2026" style labels)
- Embed roadmap on your marketing site

**Notifications**
- When a post's status changes to `Completed`: automatically email everyone who upvoted
- Customer gets "the feature you requested is live!" email
- PM gets weekly digest: top requested items with vote count trends

**Admin Dashboard**
- Posts sorted by votes, recency, or status
- Filter by board, status, date range
- CSV export of all feedback

### Phase 2

- Merge posts (combine two duplicate requests)
- Link posts to internal Jira/Linear tickets (via webhooks)
- Customer segments (tag voters by plan tier: "Enterprise customers want X most")
- Changelog posts — when you mark something Completed, auto-create a changelog entry (feed App 09)
- API for creating posts programmatically (from Intercom, Zendesk, etc.)
- CSAT survey after marking a feature complete

### AI Features

- **Smart Deduplication:** When a new post is submitted, vector similarity search (pgvector) identifies semantically similar existing posts and surfaces them before submission
- **Theme Clustering:** AI groups open feedback into emerging themes. "17 posts are all about export/reporting. 12 are about mobile performance." — surfaces non-obvious patterns
- **Priority Scoring:** AI scores each post combining vote count + number of unique companies + comment sentiment + recency — gives a `priority_score` per post
- **Response Drafting:** PM clicks "Draft Response" on a post; AI drafts a product-voice update based on the post content and your team's past updates

---

## Data Model

```
tenants (via cross-cutting)
  └─ board (many per tenant)
       └─ post (many per board)
            ├─ vote (post ↔ voter_email, unique)
            ├─ post_comment (thread on post)
            └─ post_status_history (audit of status changes)
  └─ roadmap_item (curated selection of posts, ordered)
```

**Key Tables:**

```sql
-- app/V1__feedback.sql
CREATE TABLE boards (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    name        TEXT NOT NULL,
    slug        TEXT NOT NULL,
    description TEXT,
    visibility  TEXT NOT NULL DEFAULT 'public', -- public | private
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);

CREATE TABLE feedback_posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    board_id        UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    title           TEXT NOT NULL,
    description     TEXT,
    status          TEXT NOT NULL DEFAULT 'under_review',
    -- under_review | planned | in_progress | completed | declined
    submitted_by    UUID REFERENCES cc.users(id),   -- internal user
    submitter_email TEXT,                             -- external submitter
    submitter_name  TEXT,
    vote_count      INT NOT NULL DEFAULT 0,          -- denormalized
    is_public       BOOLEAN NOT NULL DEFAULT true,
    eta_label       TEXT,                            -- "Q3 2026"
    embedding       vector(1536),                    -- pgvector for dedup
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE post_votes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id     UUID NOT NULL REFERENCES feedback_posts(id) ON DELETE CASCADE,
    voter_email TEXT NOT NULL,
    voter_name  TEXT,
    voted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(post_id, voter_email)
);

CREATE TABLE post_comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id     UUID NOT NULL REFERENCES feedback_posts(id) ON DELETE CASCADE,
    author_email TEXT NOT NULL,
    author_name TEXT,
    author_role TEXT NOT NULL DEFAULT 'customer', -- customer | team
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE post_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id     UUID NOT NULL REFERENCES feedback_posts(id),
    old_status  TEXT,
    new_status  TEXT NOT NULL,
    changed_by  UUID REFERENCES cc.users(id),
    note        TEXT,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## Cross-Cutting Module Mapping

| Module | How Used |
|--------|----------|
| **Multi-Tenancy** | Each product team is a tenant; boards, posts, votes are all tenant-scoped |
| **Auth** | Internal team authenticates via Keycloak; external submitters use email (no password — vote confirmation by email link) |
| **RBAC** | Permissions: `boards:write`, `posts:manage`, `roadmap:publish`. Role: `viewer` (team member can read but not publish roadmap) |
| **Audit** | `@Audited` on status changes — full history of who moved a feature from Planned → Completed |
| **Notifications** | On `status = completed`: fan-out email to all voters. Weekly digest email to PM. Real-time in-app alert when a post gets 10+ votes |
| **Webhooks** | Events: `post.created`, `post.status_changed`, `post.voted` — used to sync with Linear/Jira |
| **Background Jobs** | Weekly PM digest email (scheduled job). Voter fan-out on completion (queued job — could be 1000 emails). Embedding generation on post creation |
| **Search** | Full-text search on post title/description. Vector search (pgvector embeddings) for AI deduplication |
| **AI Gateway** | Deduplication, theme clustering, priority scoring, response drafting |
| **Export** | Export all posts with vote counts as CSV for offline analysis |
| **Feature Flags** | Gate "AI deduplication" and "theme clustering" behind paid plan |

---

## API Design

```
# Boards
GET    /api/boards                          List all boards for tenant
POST   /api/boards                          Create board
GET    /api/boards/{boardId}                Get board
PUT    /api/boards/{boardId}                Update board
DELETE /api/boards/{boardId}                Archive board

# Posts (authenticated team view)
GET    /api/boards/{boardId}/posts          List posts (filter by status, sort by votes)
POST   /api/boards/{boardId}/posts          Create post (on behalf of customer)
GET    /api/posts/{postId}                  Get post detail
PUT    /api/posts/{postId}/status           Update status (triggers notifications)
GET    /api/posts/{postId}/voters           List voters (emails)

# Public endpoints (no auth required)
GET    /public/{tenantSlug}/boards/{boardSlug}/posts        Public post list
POST   /public/{tenantSlug}/boards/{boardSlug}/posts        Public submission
POST   /public/{tenantSlug}/posts/{postId}/vote             Public vote (email + name)
GET    /public/{tenantSlug}/roadmap                         Public roadmap view

# Comments
GET    /api/posts/{postId}/comments         Get comments
POST   /api/posts/{postId}/comments         Post comment (team or public)

# AI
POST   /api/posts/{postId}/ai/draft-response    Draft response for PM
GET    /api/boards/{boardId}/ai/themes          Theme clustering report
```

---

## Frontend Screens

| Screen | Audience | Purpose |
|--------|----------|---------|
| **Admin Dashboard** | Team | All posts across boards, sorted by votes, filtered by status |
| **Board Detail** | Team | Posts in one board, bulk status updates |
| **Post Detail (admin)** | Team | Full post, voters list, status history, AI score, draft response |
| **Roadmap Editor** | Team | Drag-and-drop between status columns, set ETAs, toggle public visibility |
| **Public Board** | Customers | Submit feedback, upvote existing posts, add comments |
| **Public Roadmap** | Customers | Read-only view of planned/in-progress/completed items |
| **Analytics** | Team | Vote trends, top requested features by customer segment |
| **Settings** | Admin | Boards, widget embed code, webhook endpoints |

---

## Monetization

| Plan | Price | Limits |
|------|-------|--------|
| **Starter** | $0 | 1 board, 100 posts/month, yourfeedback.io subdomain |
| **Growth** | $39/month | 5 boards, unlimited posts, custom domain, webhooks, CSV export |
| **Pro** | $99/month | Unlimited boards, AI features (dedup + themes + scoring), API access, priority support |

**Billing Model:** Per team workspace, monthly or annual.

---

## Competitive Landscape

| Competitor | Gap |
|------------|-----|
| **Canny** | Good but expensive ($400/month for mid-tier); no AI features; no self-hostable option |
| **ProductBoard** | Way too complex; targets enterprise PMs; $20/seat/month adds up |
| **Frill** | Closest competitor at this price point; no AI, no webhooks, UI dated |
| **Linear** | Engineering tracker, not customer feedback; no public voting |
| **Upvoty** | Simple voting boards; no roadmap view; no webhook integration |

**Differentiation:** AI-powered deduplication (no more "I've seen this request before but can't find it"), theme clustering that surfaces patterns PMs miss, webhook integration for syncing with Linear/Jira, all at half the price of Canny.

---

## Build Phases

### Phase 1 — MVP (7 weeks)
- Board CRUD with public/private toggle
- Post submission (internal + public)
- Email-based voting (vote link → confirm → vote counted)
- Status management with status history
- Voter fan-out notifications (background job)
- Public roadmap view
- Team dashboard with vote-sorted post list

### Phase 2 — Growth (5 weeks)
- pgvector embeddings on post creation
- AI deduplication at submission time
- Webhook endpoints for Linear/Jira sync
- Post merge (combine duplicates, merge voter lists)
- Weekly PM digest email
- Embed widget (JavaScript snippet for customer apps)

### Phase 3 — Scale
- AI theme clustering and priority scoring
- Customer segments (tag voters by plan)
- Changelog integration (mark complete → auto-create changelog)
- API for creating posts from Intercom/Zendesk

---

## Success Metrics

| Stage | Signal |
|-------|--------|
| **MVP launched** | 15 product teams with ≥1 active board |
| **Early traction** | Avg 3+ customer submissions per board per week |
| **Monetization** | 25% of teams upgrade by month 3 |
| **Retention** | 80% of teams active (≥1 status update) at 60 days |
| **AI value** | Dedup suggestions accepted on ≥20% of new submissions |
