# Implementation Log - Lightweight Issue Tracker

## 2024-05-22
- Initialized the project structure for `apps/07-lightweight-issue-tracker`.
- Decision: Base package will be `com.changelog.issuetracker` to follow the convention of other modules but keep it distinct.
- Decision: Using `UUID` for IDs as per common SaaS patterns observed in `saas-os-core`.
- Decision: `content_tsv` in `issues` table will be handled as a String for now, possibly for full-text search.
- Decision: `embedding` vector(1536) will be stored as `vector` type if supported, or generic binary/json if not. Given it's Postgres, I'll assume pgvector is intended if possible, but for JPA I might use `float[]` or similar.
