-- Lightweight Issue Tracker Schema

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_projects_tenant_id ON projects(tenant_id);

CREATE TABLE labels (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(7), -- HEX color
    UNIQUE (tenant_id, name)
);

CREATE INDEX idx_labels_tenant_id ON labels(tenant_id);

CREATE TABLE issues (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    project_id UUID NOT NULL REFERENCES projects(id),
    number BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    content_tsv tsvector,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    reporter_id UUID NOT NULL,
    assignee_id UUID,
    due_date DATE,
    embedding VECTOR(1536),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, number)
);

CREATE INDEX idx_issues_tenant_id ON issues(tenant_id);
CREATE INDEX idx_issues_project_id ON issues(project_id);
CREATE INDEX idx_issues_content_tsv ON issues USING GIN(content_tsv);

CREATE TABLE issue_label_assignments (
    issue_id UUID NOT NULL REFERENCES issues(id),
    label_id UUID NOT NULL REFERENCES labels(id),
    PRIMARY KEY (issue_id, label_id)
);

CREATE TABLE issue_comments (
    id UUID PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES issues(id),
    author_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_issue_comments_issue_id ON issue_comments(issue_id);

CREATE TABLE issue_attachments (
    id UUID PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES issues(id),
    file_key VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_issue_attachments_issue_id ON issue_attachments(issue_id);

CREATE TABLE issue_links (
    id UUID PRIMARY KEY,
    source_issue_id UUID NOT NULL REFERENCES issues(id),
    target_issue_id UUID NOT NULL REFERENCES issues(id),
    link_type VARCHAR(50) NOT NULL, -- BLOCKS, RELATES_TO, DUPLICATE_OF
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_issue_links_source_id ON issue_links(source_issue_id);
CREATE INDEX idx_issue_links_target_id ON issue_links(target_issue_id);

CREATE TABLE issue_events (
    id UUID PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES issues(id),
    actor_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_issue_events_issue_id ON issue_events(issue_id);
