CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE spaces (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc_tenants(id),
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
    tenant_id       UUID NOT NULL REFERENCES cc_tenants(id),
    space_id        UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    parent_page_id  UUID REFERENCES kb_pages(id),
    title           TEXT NOT NULL,
    content         TEXT NOT NULL DEFAULT '',    -- markdown
    content_tsv     tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(content,''))) STORED,
    status          TEXT NOT NULL DEFAULT 'draft', -- draft | published
    owner_id        UUID REFERENCES cc_users(id),
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
    edited_by   UUID REFERENCES cc_users(id),
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
    tenant_id   UUID NOT NULL REFERENCES cc_tenants(id),
    user_id     UUID REFERENCES cc_users(id),
    question    TEXT NOT NULL,
    answer      TEXT,
    citations   JSONB,           -- [{page_id, title, excerpt, url}]
    feedback    TEXT,            -- helpful | not_helpful
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE search_query_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES cc_tenants(id),
    query       TEXT NOT NULL,
    result_count INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);