# MISSION
You are an autonomous principal engineer. Your mission is to implement the assigned application as a new module in this monorepo.

# ARCHITECTURE RULES
1. **Module Location**: Create the app under apps/03-ai-knowledge-base.
2. **Dependency**: Your pom.xml MUST have saas-os-parent as the parent and saas-os-core as a dependency.
3. **Core Reuse**: Use classes from com.changelog.* in saas-os-core for Billing, AI, and Multi-tenancy.
4. **Java Standard**: Use Java 21 only.

# SPECIFICATION
# App 03: AI Knowledge Base

**Tagline:** Your team's internal wiki — search it in plain English, get answers, not just links.

**Category:** Knowledge Management

---

## Problem Statement

Every team with more than 10 people struggles to find information they know exists. Documentation is spread across Notion, Confluence, Google Docs, Slack, and email. Onboarding new employees takes weeks because knowledge isn't discoverable. Experienced employees answer the same questions repeatedly.

The specific pains:
- "I know we documented this somewhere" — 10 minutes of searching, still can't find it
- New employees ask the same questions that have been asked 50 times before
- Notion pages go stale; nobody knows which version is current
- Keyword search fails because you need to know the exact phrase the author used
- Confluence is the de facto tool but it's expensive, slow, and has terrible UX

Keyword search is the root problem. People think in concepts, not exact phrases. A knowledge base with semantic AI search changes how teams retain and retrieve institutional knowledge.

---

## Target Users

**Primary Buyer:** Operations lead, CTO, or Head of Engineering at a 20–200 person company

**Primary User:** Every employee who needs to find information or write documentation

**Secondary User:** New hires during onboarding (highest ROI moment)

**Company Profile:**
- Software startups and scale-ups (engineering + product teams)
- Remote-first companies (async knowledge sharing is critical)
- Agencies with process documentation
- Any team that has outgrown Google Docs but finds Confluence too heavy

**Willingness to Pay:** $6–$15/seat/month, or $50–$300/month flat per workspace.

---

## Core Value Proposition

> Ask your knowledge base a question in plain English and get an answer with the source, not a list of links to scroll through.

---

## Feature Set

### MVP (Phase 1)

**Document Management**
- Rich text editor (similar to Notion — headings, bullets, code blocks, images, tables)
- Nested page hierarchy (spaces → sections → pages → sub-pages)
- Page versioning — every edit creates a version; diff view between versions
- Draft and Published states
- Tags and page metadata (owner, last updated, review date)
- Full file attachment support (PDFs, images stored in MinIO)

**Organization**
- Spaces (top-level containers — e.g., "Engineering", "HR", "Product")
- Permissions per space: Public to org / restricted to specific roles
- Page ownership (responsible person per page)
- Stale page detection: flag pages not edited in 90+ days

**Search**
- Keyword full-text search (PostgreSQL tsvector)
- **AI semantic search:** type a question in plain English, get the most relevant pages ranked by meaning
- Search results show a highlighted excerpt, not just the page title

**AI Q&A (Copilot)**
- "Ask" interface: user types a question, AI reads relevant pages and returns an answer with inline citations linking to source pages
- Not hallucinating: if the answer isn't in the knowledge base, AI says "I don't have this information — would you like to create a page for it?"
- Feedback buttons on AI answers: 👍 Helpful / 👎 Not helpful

### Phase 2

- `@mention` a page anywhere in the app (link preview on hover)
- Table of contents auto-generated for long pages
- Page templates (Incident Postmortem, Architecture Decision Record, Meeting Notes, Onboarding Checklist)
- Page lock (prevent edits while under review)
- Watch pages: get notified when a page is updated
- Browser extension to clip web content into the knowledge base
- Slack integration: `/kb ask [question]` returns an AI answer in Slack

### AI Features

- **Semantic Search:** pgvector embeddings on every page (chunked); similarity search over embeddings for concept-level retrieval
- **AI Q&A Copilot:** RAG pipeline — retrieve top-K relevant chunks, generate a cited answer via LiteLLM gateway
- **Auto-Tagging:** AI suggests relevant tags when a page is created or edited
- **Gap Detection:** Periodically analyze common search queries with no good results; surface "your team searched for X but found nothing — consider writing a page about it"
- **Stale Page Summary:** AI reads a stale page and writes a one-paragraph summary of what it says, helping the owner decide whether to update or archive it

---

## Data Model

```
tenants (via cross-cutting)
  └─ space (top-level container, RBAC per space)
       └─ page (recursive: pages have parent_page_id)
            ├─ page_version (content snapshots per edit)
            ├─ page_chunk (split content, vector embedding per chunk)
            ├─ page_attachment (files stored in MinIO)
            └─ page_view (analytics: who viewed, when)
  └─ search_query_log (for gap detection)
  └─ ai_qa_session (chat history with citations)
```

**Key Tables:**

```sql
-- app/V1__knowledge_base.sql
CREATE TABLE spaces (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    name        TEXT NOT NULL,
    slug        TEXT NOT NULL,
    description TEXT,
    visibility  TEXT NOT NULL DEFAULT 'org', -- org | restricted
    icon        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);

CREATE TABLE kb_pages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    space_id        UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    parent_page_id  UUID REFERENCES kb_pages(id),
    title           TEXT NOT NULL,
    content         TEXT NOT NULL DEFAULT '',    -- markdown
    content_tsv     tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(content,''))) STORED,
    status          TEXT NOT NULL DEFAULT 'draft', -- draft | published
    owner_id        UUID REFERENCES cc.users(id),
    position        INT NOT NULL DEFAULT 0,
    tags            TEXT[] NOT NULL DEFAULT '{}',
    last_reviewed_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX kb_pages_tsv_idx ON kb_pages USING GIN(content_tsv);

CREATE TABLE page_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id     UUID NOT NULL REFERENCES kb_pages(id) ON DELETE CASCADE,
    version_num INT NOT NULL,
    content     TEXT NOT NULL,
    title       TEXT NOT NULL,
    edited_by   UUID REFERENCES cc.users(id),
    edited_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE page_chunks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id     UUID NOT NULL REFERENCES kb_pages(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1536),    -- pgvector embedding (OpenAI text-embedding-3-small)
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX page_chunks_embedding_idx ON page_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE TABLE ai_qa_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    user_id     UUID REFERENCES cc.users(id),
    question    TEXT NOT NULL,
    answer      TEXT,
    citations   JSONB,           -- [{page_id, title, excerpt, url}]
    feedback    TEXT,            -- helpful | not_helpful
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE search_query_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc.tenants(id),
    query       TEXT NOT NULL,
    result_count INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## Cross-Cutting Module Mapping

| Module | How Used |
|--------|----------|
| **Multi-Tenancy** | Each company is a tenant; all spaces, pages, and AI sessions are tenant-scoped |
| **Auth** | All access requires Keycloak authentication |
| **RBAC** | Space-level permissions: `spaces:read`, `spaces:write`, `pages:publish`, `pages:delete`. Admin can restrict spaces to specific roles |
| **Audit** | `@Audited` on page publish, page delete, space visibility change |
| **Notifications** | Notify page owner when page becomes stale. Notify watchers when a page they watch is edited |
| **Background Jobs** | Async embedding generation on page save (don't block the editor save). Weekly stale page report. Gap detection analysis (weekly cron) |
| **Search** | Full-text search via `content_tsv`. Vector search via `page_chunks.embedding` for semantic Q&A retrieval |
| **File Storage** | Page attachments stored in MinIO with presigned URLs |
| **AI Gateway** | Embedding generation (text-embedding-3-small), Q&A answer generation (gpt-4o or claude-3-5-sonnet), auto-tagging, stale page summary |
| **Feature Flags** | Gate "AI Q&A Copilot" and "gap detection" behind paid plan |
| **Export** | Export a space as a ZIP of markdown files |

---

## RAG Pipeline Detail

The AI Q&A uses Retrieval-Augmented Generation:

```
1. User submits question: "How do we handle database migrations in production?"

2. Generate question embedding (text-embedding-3-small via AI gateway)

3. Vector similarity search:
   SELECT page_id, content, 1 - (embedding <=> $questionEmbedding) AS score
   FROM page_chunks
   WHERE tenant_id = $tenantId
   ORDER BY embedding <=> $questionEmbedding
   LIMIT 8;

4. Fetch full page titles for matched chunks

5. Build LLM prompt:
   "You are a knowledge assistant. Answer the user's question using ONLY the
   context below. If the answer is not in the context, say so.
   
   Context: [top-8 chunks concatenated]
   
   Question: How do we handle database migrations in production?
   
   Answer with citations [Page Title](url)."

6. Stream response back to user via SSE

7. Store session in ai_qa_sessions with citations JSON
```

---

## API Design

```
# Spaces
GET    /api/spaces                          List spaces (RBAC-filtered)
POST   /api/spaces                          Create space
GET    /api/spaces/{spaceId}                Get space with page tree

# Pages
GET    /api/spaces/{spaceId}/pages          List pages in space (tree structure)
POST   /api/spaces/{spaceId}/pages          Create page
GET    /api/pages/{pageId}                  Get page (latest version)
PUT    /api/pages/{pageId}                  Save page (creates new version)
PUT    /api/pages/{pageId}/publish          Publish draft page
DELETE /api/pages/{pageId}                  Archive page

GET    /api/pages/{pageId}/versions         Version history
GET    /api/pages/{pageId}/versions/{num}   Get specific version

# Attachments
POST   /api/pages/{pageId}/attachments      Get presigned upload URL
GET    /api/pages/{pageId}/attachments      List attachments

# Search
GET    /api/search?q=query&type=keyword|semantic    Search pages

# AI
POST   /api/ai/ask                          Q&A: {question} → {answer, citations}
POST   /api/ai/ask/{sessionId}/feedback     Submit feedback (helpful/not)
GET    /api/ai/gaps                         Gap report (frequently searched with no results)
POST   /api/pages/{pageId}/ai/suggest-tags  AI tag suggestions
POST   /api/pages/{pageId}/ai/summarize     Stale page summary
```

---

## Frontend Screens

| Screen | Purpose |
|--------|---------|
| **Home** | Recent pages, my pages, spaces list, search bar |
| **AI Copilot** | Prominent "Ask anything" chat interface with source citations |
| **Space View** | Page tree (nested sidebar), space description |
| **Page View** | Rendered markdown with ToC, last edited, owner, version badge |
| **Page Editor** | Rich text editor (tiptap/ProseMirror), auto-save, tag picker |
| **Search Results** | Combined keyword + semantic results with excerpts |
| **Stale Pages** | Admin view of pages flagged for review |
| **Analytics** | Top searched queries, pages with no results (gaps), popular pages |

---

## Monetization

| Plan | Price | Limits |
|------|-------|--------|
| **Starter** | $0 | 3 users, 1 space, 50 pages, keyword search only |
| **Team** | $8/seat/month | Unlimited users, 10 spaces, 500 pages, AI semantic search |
| **Business** | $14/seat/month | Unlimited spaces + pages, AI Q&A Copilot, gap detection, Slack integration, CSV export |

**Billing Model:** Per-seat, monthly or annual (20% discount).

---

## Competitive Landscape

| Competitor | Gap |
|------------|-----|
| **Confluence** | Heavy, slow, expensive ($5.75–$11/seat); terrible search UX |
| **Notion** | Excellent editor but search is keyword-only; not purpose-built as a KB |
| **Guru** | Good AI search; $10/seat but steep minimum; UI is clunky |
| **Slite** | Clean UI; no AI Q&A; lacks semantic search |
| **Tettra** | Built for Slack-first teams; no AI; limited editor |

**Differentiation:** The AI Copilot answers questions directly from your own content (not the internet) with source citations, which is the killer feature. The vector search means people find things even when they don't know the exact terminology. The clean editor makes people actually want to write documentation (the Notion effect).

---

## Build Phases

### Phase 1 — MVP (9 weeks)
- Space + page CRUD
- Rich text editor (tiptap) with auto-save and versioning
- Tags, ownership, draft/published status
- Full-text keyword search (PostgreSQL tsvector)
- File attachments via MinIO presigned URLs
- Stale page flag (cron job)
- Notifications to page watchers on edits

### Phase 2 — AI (6 weeks)
- pgvector embeddings (background job on page save)
- Semantic search endpoint
- AI Q&A Copilot (RAG pipeline via LiteLLM gateway)
- Answer feedback (helpful/not helpful)
- Auto-tag suggestions

### Phase 3 — Scale
- Gap detection analysis
- Slack integration (`/kb ask`)
- Browser extension for web clipping
- Space-level permission groups
- Page templates library

---

## Success Metrics

| Stage | Signal |
|-------|--------|
| **MVP launched** | 10 teams with ≥20 pages created |
| **AI value** | 40%+ of search queries use semantic search |
| **Copilot adoption** | 30%+ of DAU use AI Ask at least once per week |
| **Retention** | Pages per team growing month-over-month (content flywheel) |
| **Monetization** | Seat expansion within teams (word of mouth internally) |
