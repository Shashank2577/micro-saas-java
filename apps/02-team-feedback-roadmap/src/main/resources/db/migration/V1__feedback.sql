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
