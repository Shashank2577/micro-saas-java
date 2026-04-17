# App 09: Changelog & Release Notes Platform

**Tagline:** Publish product updates professionally and automatically notify everyone who cares.

**Category:** Product Management / Customer Communication

**Implementation:** [changelog-platform/README.md](../../changelog-platform/README.md)

---

## Current Implementation Status

**Status: In Progress** — Backend complete. Frontend, email delivery, and JS widget not yet built.

### Done

| Area | What's built |
|------|-------------|
| Core changelog | Projects, posts (Draft/Scheduled/Published), tags, full-text search column, scheduled publishing field |
| Public changelog API | `GET /changelog/{slug}` — unauthenticated, paginated, published posts only |
| Widget config | Position, colours, allowed origins stored per project |
| Subscriber management | Opt-in/opt-out, segmentation by plan tier |
| Acquisition module | Landing pages with A/B variant testing, conversion tracking |
| Monetisation module | Stripe products, subscriptions, webhook handler (created/updated/deleted/payment events) |
| Retention module | Drip campaign engine — templates, enrollments, hourly step processor, wired into Stripe events |
| Success module | Customer health scoring (0–100 + risk signals), support tickets (AI-classified, sentiment scoring) |
| Intelligence module | Analytics event tracking, funnel analysis, unit economics (MRR/ARR/CAC/LTV) |
| Multi-tenancy | Every row scoped to `tenant_id`, Keycloak JWT extracts tenant context |
| Local dev | Docker Compose (postgres:5433, keycloak, minio), `local` Spring profile (no Keycloak required) |
| Database | Flyway V1–V4 migrations, 20+ tables, JSONB for flexible metadata |

### Not Yet Built

| Area | Notes |
|------|-------|
| JavaScript widget | CDN-hosted `<script>` embed, "What's New" bell icon |
| Email delivery | Subscriber notifications on post publish; drip sends (currently logged only) |
| RSS feed | `GET /changelog/{slug}/feed.xml` |
| Frontend UI | React or Thymeleaf — admin dashboard and public changelog page |
| Custom domain | CNAME to platform, SSL via Let's Encrypt |
| Scheduled publishing | `scheduledFor` field exists; background job not wired |
| AI post generation | LiteLLM gateway configured; integration pending |
| Stripe checkout | Products modelled; checkout session flow not built |
| Full-text search endpoint | PostgreSQL `tsvector` column exists; search API not exposed |
| File uploads (MinIO) | Config present; upload endpoint missing |

---

## Problem Statement

Most SaaS companies ship updates but don't communicate them well. Their changelog is a GitHub release page (developer-only), a Notion doc nobody reads, or a "What's New" section that hasn't been updated in three months. Customers don't know about new features, miss quality-of-life improvements, and form an impression that the product is stagnant — even when the team ships weekly.

The specific pains:
- Features get shipped but never announced → customers don't adopt them → perceived value drops
- Email newsletters are batch quarterly — no connection between the ship and the announcement
- Support tickets for features that already exist: "Do you have X?" — yes, we built it six months ago
- Developers hate writing marketing copy; product marketing doesn't know what shipped
- No embeddable changelog widget for inside the app ("What's New" bell icon)
- No subscriber management: who wants to be notified of updates?

The business cost: customers churn because they don't see progress. The solution is infrastructure that makes publishing updates frictionless and delivery automatic.

---

## Target Users

**Primary Buyer:** Head of Product or Head of Marketing at a B2B SaaS company

**Primary User:** Product manager or technical writer drafting release notes

**End Consumer:** The SaaS company's customers reading updates

**Company Profile:**
- B2B SaaS companies with 50–5000 customers
- Developer tools and API-first products (technical audience)
- Consumer apps that ship weekly updates
- Any team that wants to look more active and communicate better

**Willingness to Pay:** $20–$100/month — marketing budget, not engineering budget.

---

## Core Value Proposition

> Ship a feature, write one update, and it reaches every customer who cares — in-app, by email, and on a public changelog page — automatically.

---

## Feature Set

### MVP (Phase 1)

**Changelog Posts**
- Rich text editor for writing updates (markdown + images)
- Post metadata: title, summary (one sentence), tags (categories), publish date
- Category tags: New Feature, Improvement, Bug Fix, Deprecation, Breaking Change
- Post status: Draft / Scheduled / Published
- Scheduled publishing (set a future date and time)
- Header image upload per post

**Public Changelog Page**
- Hosted at `{yourproduct}.changelog.io` or custom domain
- Clean, filterable feed of all published updates
- Filter by category, search by keyword
- RSS feed (machine-readable, for users who prefer RSS readers)
- Social share link per post

**In-App Widget**
- JavaScript snippet: embed a "What's New" bell icon in your product
- Widget shows unread posts with a dot indicator
- Clicking opens a slide-over panel with the latest updates
- "Mark as read" per post (tracked per visitor via localStorage or identifier)
- Fully customizable: position, colors, icon

**Subscriber Notifications**
- Email subscription: "Subscribe to updates" form on public page
- Subscriber list management (add, export, unsubscribe)
- On publish: auto-send email newsletter to all subscribers
- Subscriber segmentation by plan tier (send "Enterprise" updates only to enterprise customers)

**Analytics**
- Post views count
- Email open rate per post
- Subscriber count over time
- Widget opens per week

### Phase 2

- Multi-product changelogs (one account, multiple products)
- Changelog reactions (👍 💡 ❤️ per post — customers can react)
- Comments on posts (moderated)
- Integration with Linear: publishing a post automatically links resolved issues
- Integration with GitHub: post auto-drafted from merged PR titles + release notes
- Changelog digest email (weekly or monthly roundup, not per-post)
- Unsubscribe management with reasons (optional survey)
- Private changelog (behind login — for internal teams or beta users)

### AI Features

- **Release Notes Writer:** Engineer describes what they shipped in plain language ("Fixed the bug where search didn't work with special characters and added a new export to Excel button"). AI rewrites it as professional release notes in product-marketing voice
- **Title Generator:** AI suggests 3 attention-grabbing titles for a post based on the content ("Search just got smarter", "Export your data in one click", "That search bug? Fixed for good")
- **Tag Suggester:** AI reads the post and suggests appropriate category tags
- **Newsletter Subject Line Generator:** AI generates 3 A/B-test subject line options for the email notification ("🚀 New: Export to Excel is here", "We fixed your most-requested thing", "Your Monday productivity just improved")
- **Summary Extractor:** Given a long post, AI writes a one-sentence summary for the email preview text and social share card

---

## Data Model

```
tenants (via cross-cutting)
  └─ changelog_project (one per product — company may have multiple products)
       ├─ changelog_post
       │    ├─ post_tag (many-to-many with tag)
       │    └─ post_view_event (analytics)
       ├─ tag (category definitions)
       └─ subscriber (email subscribers)
            └─ subscriber_notification (which emails sent to which subscriber)
  └─ widget_config (per project)
  └─ custom_domain (per project)
```

**Key Tables:**

```sql
-- app/V1__changelog.sql
CREATE TABLE changelog_projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    name            TEXT NOT NULL,
    slug            TEXT NOT NULL,
    description     TEXT,
    logo_key        TEXT,      -- MinIO key for project logo
    favicon_key     TEXT,
    custom_domain   TEXT,
    branding        JSONB NOT NULL DEFAULT '{}',  -- colors, font
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);

CREATE TABLE changelog_tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES changelog_projects(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    color       TEXT NOT NULL DEFAULT '#6B7280',
    UNIQUE(project_id, name)
);

CREATE TABLE changelog_posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL REFERENCES changelog_projects(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    title           TEXT NOT NULL,
    summary         TEXT,
    content         TEXT NOT NULL DEFAULT '',     -- markdown
    content_tsv     tsvector GENERATED ALWAYS AS
                    (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(content,''))) STORED,
    header_image_key TEXT,
    status          TEXT NOT NULL DEFAULT 'draft',  -- draft | scheduled | published
    published_at    TIMESTAMPTZ,
    scheduled_for   TIMESTAMPTZ,
    author_id       UUID REFERENCES cc.users(id),
    view_count      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX changelog_posts_tsv_idx ON changelog_posts USING GIN(content_tsv);

CREATE TABLE post_tag_assignments (
    post_id     UUID NOT NULL REFERENCES changelog_posts(id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES changelog_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);

CREATE TABLE changelog_subscribers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES changelog_projects(id) ON DELETE CASCADE,
    email       TEXT NOT NULL,
    name        TEXT,
    plan_tier   TEXT,       -- for segmentation (populated by buyer via API)
    status      TEXT NOT NULL DEFAULT 'active',  -- active | unsubscribed
    subscribed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unsubscribed_at TIMESTAMPTZ,
    UNIQUE(project_id, email)
);

CREATE TABLE subscriber_notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES changelog_posts(id),
    subscriber_id   UUID NOT NULL REFERENCES changelog_subscribers(id),
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    opened_at       TIMESTAMPTZ,   -- email open tracking pixel
    clicked_at      TIMESTAMPTZ
);

CREATE TABLE widget_configs (
    project_id      UUID PRIMARY KEY REFERENCES changelog_projects(id),
    position        TEXT NOT NULL DEFAULT 'bottom-right',  -- bottom-right | bottom-left | top-right
    trigger_type    TEXT NOT NULL DEFAULT 'badge',         -- badge | button
    badge_label     TEXT NOT NULL DEFAULT "What's New",
    primary_color   TEXT NOT NULL DEFAULT '#4F46E5',
    allowed_origins TEXT[] NOT NULL DEFAULT '{}'
);
```

---

## Cross-Cutting Module Mapping

| Module | How Used |
|--------|----------|
| **Multi-Tenancy** | Each company is a tenant; changelog projects and posts are tenant-scoped |
| **Auth** | Team members (editors) authenticate via Keycloak; public readers and widget visitors are unauthenticated |
| **RBAC** | Permissions: `posts:create`, `posts:publish`, `posts:delete`, `subscribers:manage`. Role: `editor` (write but not publish), `publisher` |
| **Audit** | `@Audited` on `publishPost()`, `deletePost()`, `schedulePost()` |
| **Notifications** | Internal: notify team when post is published. External: notify subscribers via email (fan-out background job) |
| **Background Jobs** | Scheduled post publisher (cron: check posts with `status=scheduled` and `scheduled_for <= now()`). Email fan-out on publish (queue job per subscriber batch). Weekly/monthly digest email |
| **File Storage** | Header images and logos stored in MinIO with presigned upload URLs |
| **Search** | Full-text search on public changelog page (PostgreSQL tsvector) |
| **Webhooks** | Events: `post.published` — buyer's platform can react (e.g., post to Slack, update in-app notification) |
| **AI Gateway** | Release notes writer, title generator, tag suggester, newsletter subject line, summary extractor |
| **Feature Flags** | Gate "custom domain", "AI writing assistant", "subscriber segmentation" behind paid plan |
| **Export** | Export subscriber list as CSV |

---

## API Design

```
# Projects
GET    /api/projects                          List projects
POST   /api/projects                          Create project
GET    /api/projects/{projectId}              Get project with config
PUT    /api/projects/{projectId}              Update project
DELETE /api/projects/{projectId}              Archive project

# Posts
GET    /api/projects/{projectId}/posts        List posts (filter: status, tag)
POST   /api/projects/{projectId}/posts        Create post
GET    /api/posts/{postId}                    Get post
PUT    /api/posts/{postId}                    Update post
POST   /api/posts/{postId}/publish            Publish post now
POST   /api/posts/{postId}/schedule           Schedule post
DELETE /api/posts/{postId}                    Delete post

# Subscribers
GET    /api/projects/{projectId}/subscribers  List subscribers
POST   /api/projects/{projectId}/subscribers  Add subscriber (or via sync API)
DELETE /api/subscribers/{subscriberId}        Unsubscribe

# Tags
GET    /api/projects/{projectId}/tags         List tags
POST   /api/projects/{projectId}/tags         Create tag

# Public page (no auth)
GET    /p/{projectSlug}                       Public changelog page (HTML)
GET    /p/{projectSlug}/feed.rss              RSS feed
GET    /api/public/{projectSlug}/posts        JSON API for widget

# Widget (no auth, origin-checked)
GET    /widget/{projectSlug}/posts            Latest posts for widget
POST   /widget/{projectSlug}/read             Mark posts as read for this visitor

# Subscriber sign-up (no auth)
POST   /subscribe/{projectSlug}               Subscribe to changelog
GET    /unsubscribe/{token}                   One-click unsubscribe

# AI
POST   /api/posts/{postId}/ai/rewrite         AI rewrites draft as release notes
POST   /api/posts/{postId}/ai/suggest-title   Generate 3 title options
POST   /api/posts/{postId}/ai/suggest-tags    Suggest category tags
POST   /api/posts/{postId}/ai/email-subject   Generate email subject lines

# Analytics
GET    /api/projects/{projectId}/analytics    View counts, subscriber growth, email opens
```

---

## Frontend Screens

| Screen | Audience | Purpose |
|--------|----------|---------|
| **Post List** | Team | All posts with status badges, draft/published/scheduled |
| **Post Editor** | Team | Rich text editor, tag picker, schedule, AI writing tools |
| **Public Changelog** | Customers | Hosted public feed, filterable by tag, searchable |
| **Widget Preview** | Team | Live preview of the in-app widget appearance |
| **Subscriber Manager** | Team | List, filter, export subscribers; view open rates per post |
| **Analytics Dashboard** | Team | Post views, subscriber growth, email engagement |
| **Settings** | Team | Branding, custom domain, widget config, integrations |

---

## Monetization

| Plan | Price | Limits |
|------|-------|--------|
| **Free** | $0 | 1 project, 500 subscribers, yourproject.changelog.io |
| **Startup** | $29/month | 1 project, 5,000 subscribers, custom domain, in-app widget, RSS |
| **Growth** | $79/month | 3 projects, 25,000 subscribers, AI writing assistant, subscriber segmentation, webhooks |

---

## Competitive Landscape

| Competitor | Gap |
|------------|-----|
| **Headway** | Simplest changelog tool; $29/month; no AI; no subscriber segmentation; no webhooks |
| **Beamer** | Strong competitor; $49/month; in-app widget; no AI writing assistant |
| **Featurebase** | Combines feedback + changelog; $40/month; heavier than needed if you only want changelog |
| **LaunchNotes** | Enterprise-focused; $79/month minimum; no in-app widget on base plan |
| **AnnounceKit** | $49/month; good widget; no AI features |

**Differentiation:** AI release notes writer (developers can write casually, AI makes it sound professional), subscriber segmentation by plan tier (enterprise customers get different announcements), webhook integration, and a price point that makes it a no-brainer for early-stage teams.

---

## Build Phases

### Phase 1 — MVP (6 weeks)
- Project + post CRUD
- Rich text editor with image upload
- Category tags
- Public changelog page (hosted HTML)
- RSS feed
- Email subscriber sign-up + unsubscribe
- Email fan-out on publish (background job)
- In-app widget (JavaScript snippet)
- Basic analytics (view count, subscriber count)

### Phase 2 — Growth (5 weeks)
- Post scheduling (cron job)
- Custom domain support
- Subscriber segmentation by plan tier
- Email open tracking
- Webhook on `post.published`
- Subscriber list CSV export

### Phase 3 — Scale
- AI release notes writer
- AI title generator and subject line generator
- Multiple projects per account
- GitHub PR integration (auto-draft from PR)
- Linear integration (link resolved issues to posts)
- Monthly digest email option

---

## Success Metrics

| Stage | Signal |
|-------|--------|
| **MVP launched** | 20 SaaS teams with ≥1 published post |
| **Adoption** | Average 2+ posts published per team per month |
| **Email** | Email open rates above industry average (25%+ for product updates) |
| **Widget** | 30%+ of teams have widget installed and getting opens |
| **Monetization** | 15% free-to-paid conversion (lower because free tier is generous, virality is the growth loop) |
| **Virality** | "Powered by [App]" on free changelogs drives organic signups |
