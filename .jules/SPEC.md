# Specification: Lightweight Issue Tracker

## Data Model

### Projects
- `id` (UUID, PK)
- `tenant_id` (UUID, indexed)
- `name` (String)
- `slug` (String, unique within tenant)
- `description` (Text)
- `status` (String: ACTIVE, ARCHIVED)
- `created_by` (UUID)
- `created_at` (Timestamp)
- `updated_at` (Timestamp)

### Labels
- `id` (UUID, PK)
- `tenant_id` (UUID, indexed)
- `name` (String)
- `color` (String)

### Issues
- `id` (UUID, PK)
- `tenant_id` (UUID, indexed)
- `project_id` (UUID, FK)
- `number` (Long, auto-incrementing per project)
- `title` (String)
- `description` (Text)
- `content_tsv` (TSVector for search)
- `status` (String: OPEN, IN_PROGRESS, RESOLVED, CLOSED)
- `priority` (String: LOW, MEDIUM, HIGH, URGENT)
- `reporter_id` (UUID)
- `assignee_id` (UUID)
- `due_date` (Date)
- `embedding` (Vector 1536)
- `created_at` (Timestamp)
- `updated_at` (Timestamp)

### Issue Label Assignments
- `issue_id` (UUID, FK)
- `label_id` (UUID, FK)

### Issue Comments
- `id` (UUID, PK)
- `issue_id` (UUID, FK)
- `author_id` (UUID)
- `content` (Text)
- `created_at` (Timestamp)

### Issue Attachments
- `id` (UUID, PK)
- `issue_id` (UUID, FK)
- `file_key` (String)
- `file_name` (String)
- `file_size_bytes` (Long)
- `created_at` (Timestamp)

### Issue Links
- `id` (UUID, PK)
- `source_issue_id` (UUID, FK)
- `target_issue_id` (UUID, FK)
- `link_type` (String: BLOCKS, RELATES_TO, DUPLICATE_OF)

### Issue Events
- `id` (UUID, PK)
- `issue_id` (UUID, FK)
- `actor_id` (UUID)
- `event_type` (String)
- `old_value` (String)
- `new_value` (String)
- `created_at` (Timestamp)

## API Endpoints

- `GET/POST /api/projects`
- `GET/PUT/DELETE /api/projects/{projectId}`
- `GET/POST /api/projects/{projectId}/issues`
- `GET/PUT/DELETE /api/issues/{issueId}`
- `GET /api/issues/{issueId}/activity` (mapped to issue_events)
- `GET/POST /api/issues/{issueId}/comments`
- `PUT/DELETE /api/comments/{commentId}`
- `GET/POST /api/labels`
- `GET /api/issues/search?q=query`
- `POST /api/issues/ai/check-duplicate`
- `POST /api/issues/{issueId}/ai/suggest-priority`
