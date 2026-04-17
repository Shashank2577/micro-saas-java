# 01 — Public Changelog: Feeds, Search, HTML View

Status: proposed
Owner: platform team
Related code: `src/main/java/com/changelog/controller/PublicChangelogController.java`, `src/main/java/com/changelog/controller/RssFeedController.java`, `src/main/java/com/changelog/controller/PublicController.java`, `src/main/resources/db/migration/V1__init.sql`.

## Overview

Today the public changelog is JSON-only. `PublicChangelogController.java:35` serves `GET /changelog/{slug}` as a `PublicChangelogResponse` JSON payload; there is a partial `RssFeedController.java:33` wired at `/changelog/{slug}/feed.xml` (RSS 2.0 only); `PublicController.java:21` at `/p/{tenantSlug}/{projectSlug}` also returns JSON. None of this is crawler-, reader-, or share-link-friendly.

This plan turns the public surface into a real product by adding three features:

1. **Atom + RSS feeds** at `GET /changelog/{slug}/feed.atom` and `GET /changelog/{slug}/feed.rss` using Rome 2.1.0 (already in `pom.xml:106-110`), replacing the single `/feed.xml` endpoint with a content-type-aware, format-aware pair.
2. **Full-text search** at `GET /changelog/{slug}/search?q=...`. V1 already stamps a `content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', ...)) STORED` column on `changelog_posts` (`V1__init.sql:63-64`) and a `GIN` index (`V1__init.sql:75`). `PostRepository.searchPublishedPostsByProject` (`PostRepository.java:40-48`) already runs the native ts_rank query but nothing surfaces it — the orphaned import of `SearchResultResponse` recently removed from `PublicChangelogController.java:25` confirms a prior abandoned attempt.
3. **Server-rendered HTML changelog page** at `GET /p/{slug}`. The existing `/p/{tenantSlug}/{projectSlug}` JSON endpoint is retained unchanged for backwards compatibility; the new route renders Thymeleaf HTML that a customer can share on Twitter/LinkedIn, that Googlebot can crawl, and that is `<link rel="alternate" type="application/atom+xml">`-wired to the feeds from #1.

No schema changes are required for #1 and #2 (V1 already supplies everything). #3 adds a single `spring-boot-starter-thymeleaf` dependency and templates; no DB migration.

## Goals & Non-goals

**Goals**

- Atom and RSS endpoints return valid feeds that pass `feedvalidator.org` and load in NetNewsWire / Feedly without warnings.
- `/search?q=...` returns ranked published posts for a single project, scoped by the `content_tsv` index — O(log n), no table scans.
- `/p/{slug}` returns a semantic HTML5 page (`<article>` per post, `<time datetime>` tags, Open Graph meta, `<link rel="alternate">` to feeds) that a non-JS crawler can read.
- `SchemaValidationIT` (`src/test/java/com/changelog/SchemaValidationIT.java`) stays green: no new entity fields, no new columns.
- Security remains unchanged in `SecurityConfig.java:23-26` — `/changelog/**` and `/p/**` are already `permitAll`.

**Non-goals**

- Custom domain / CNAME handling (future; `custom_domain` column already exists on `changelog_projects`).
- Pagination on the HTML view beyond a single "load more" JSON link — server pagination can come later.
- Per-tenant theming beyond primary color + logo; the existing `branding JSONB` is read but not extended.
- Authenticated reader features (subscribe-from-HTML form stays out of scope — the API already exists at `POST /changelog/{slug}/subscribe`; the HTML view will link to it but not re-implement it).
- Search facets, tag filters, date-range filters — v1 is a single `q` param only.
- Replacing or deprecating the existing `/p/{tenantSlug}/{projectSlug}` JSON endpoint in `PublicController.java:21`. It stays; the new `/p/{slug}` is additive.
- Deleting the existing `RssFeedController` `/feed.xml` endpoint: it stays as a redirect alias to `/feed.rss` for one release, then gets removed in a follow-up.

## Acceptance criteria

1. `curl -sS -H "Accept: application/atom+xml" http://localhost:8081/changelog/demo-project/feed.atom | xmllint --noout -` exits 0 and the body's root element is `<feed xmlns="http://www.w3.org/2005/Atom">` containing one `<entry>` per published post from the V2 seed data.
2. `curl -sS http://localhost:8081/changelog/demo-project/feed.rss | xmllint --xpath "string(/rss/@version)" -` prints `2.0`, and each `<item>` has `<title>`, `<link>`, `<guid isPermaLink="false">`, `<pubDate>` (RFC-822), and `<description>`.
3. Both feed endpoints return `Content-Type: application/atom+xml;charset=UTF-8` and `application/rss+xml;charset=UTF-8` respectively, and 404 for an unknown slug.
4. `curl -sS "http://localhost:8081/changelog/demo-project/search?q=feature"` returns HTTP 200 with JSON `{"query":"feature","total":N,"results":[{"id":"...","title":"...","summary":"...","publishedAt":"...","rank":0.x}, ...]}` ordered by descending `rank`.
5. With `q` empty or missing, `/search` returns HTTP 400 with a JSON error body (`{"error":"query parameter 'q' is required"}`).
6. `/search` against an unknown slug returns HTTP 404.
7. `curl -sS http://localhost:8081/p/demo-project | grep -q "<!doctype html>"` — a full HTML5 doc is served; `Content-Type: text/html;charset=UTF-8`.
8. The HTML page contains exactly one `<h1>` with the project name, one `<article>` per published post, a `<link rel="alternate" type="application/atom+xml" href=".../feed.atom">` in `<head>`, and Open Graph meta (`og:title`, `og:description`, `og:type=website`).
9. The HTML page renders correctly when the project has zero published posts (empty state, no server error).
10. `/p/{unknown-slug}` returns HTTP 404 with a rendered 404 page (not a JSON error).
11. `mvn -q -Dspring-boot.run.profiles=local test` runs `SchemaValidationIT` to green — no new entity↔column drift.
12. Search query response time is under 50 ms for a project with 10k posts on the dev Postgres (verified with `EXPLAIN ANALYZE` showing `Bitmap Index Scan on idx_changelog_posts_tsv`).

## User-facing surface

All endpoints are public (no auth), `tenant_id` is derived from the project's slug on the server side, never from a client header.

### 1. Atom feed

```
GET  /changelog/{slug}/feed.atom
Auth: none (already permitAll in SecurityConfig.java:24)
Produces: application/atom+xml;charset=UTF-8
```

Success example (truncated):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Demo Project Changelog</title>
  <subtitle>Release notes for Demo Project</subtitle>
  <link rel="self" href="http://localhost:8081/changelog/demo-project/feed.atom"/>
  <link rel="alternate" type="text/html" href="http://localhost:8081/p/demo-project"/>
  <id>urn:uuid:{project-uuid}</id>
  <updated>2026-04-16T10:12:44Z</updated>
  <entry>
    <title>v1.2 — AI-assisted drafting</title>
    <id>urn:uuid:{post-uuid}</id>
    <link rel="alternate" type="text/html" href="http://localhost:8081/p/demo-project#post-{post-uuid}"/>
    <published>2026-04-10T14:00:00Z</published>
    <updated>2026-04-10T14:00:00Z</updated>
    <summary type="text">We shipped AI rewrite ...</summary>
    <content type="html"><![CDATA[<h2>What’s new</h2>...]]></content>
  </entry>
</feed>
```

Errors: 404 (unknown slug), 500 (Rome `FeedException` — logged, opaque body).

### 2. RSS feed

```
GET  /changelog/{slug}/feed.rss
Auth: none
Produces: application/rss+xml;charset=UTF-8
```

Success example (truncated):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Demo Project Changelog</title>
    <link>http://localhost:8081/p/demo-project</link>
    <description>Release notes for Demo Project</description>
    <atom:link href="http://localhost:8081/changelog/demo-project/feed.rss" rel="self" type="application/rss+xml" xmlns:atom="http://www.w3.org/2005/Atom"/>
    <item>
      <title>v1.2 — AI-assisted drafting</title>
      <link>http://localhost:8081/p/demo-project#post-{post-uuid}</link>
      <guid isPermaLink="false">urn:uuid:{post-uuid}</guid>
      <pubDate>Fri, 10 Apr 2026 14:00:00 +0000</pubDate>
      <description>We shipped AI rewrite ...</description>
    </item>
  </channel>
</rss>
```

Errors: 404 (unknown slug), 500 on feed render failure.

### 3. Legacy `/feed.xml` alias

```
GET  /changelog/{slug}/feed.xml
```
Returns HTTP 301 → `/changelog/{slug}/feed.rss`. Kept for one release so existing subscribers in the wild don't break.

### 4. Full-text search

```
GET  /changelog/{slug}/search?q=<query>&limit=<int>
Auth: none
Query params:
  q      required, 1..200 chars, trimmed
  limit  optional, default 20, max 50
Produces: application/json
```

Request example:
```
curl -sS "http://localhost:8081/changelog/demo-project/search?q=AI%20drafting&limit=5"
```

Success response (HTTP 200):
```json
{
  "query": "AI drafting",
  "total": 2,
  "limit": 5,
  "results": [
    {
      "id": "7b2c...",
      "title": "v1.2 — AI-assisted drafting",
      "summary": "We shipped AI rewrite ...",
      "publishedAt": "2026-04-10T14:00:00",
      "rank": 0.18
    },
    {
      "id": "a44e...",
      "title": "v1.1 — Draft autosave",
      "summary": "Drafts now save every 5s.",
      "publishedAt": "2026-03-22T09:12:00",
      "rank": 0.05
    }
  ]
}
```

Error codes:
| Code | When | Body |
|------|------|------|
| 400 | `q` missing, blank, or over 200 chars | `{"error":"query parameter 'q' is required"}` / `"...'q' must be 1..200 chars"` |
| 400 | `limit < 1` or `> 50` | `{"error":"limit must be between 1 and 50"}` |
| 404 | Unknown slug | `{"error":"changelog not found"}` |
| 500 | DB error | opaque `{"error":"internal error"}`, logged server-side |

### 5. Server-rendered HTML page

```
GET  /p/{slug}
Auth: none (permitAll in SecurityConfig.java:23)
Produces: text/html;charset=UTF-8
```

Rendered HTML contents (structure, not wording):
- `<head>` — title = "{Project Name} Changelog", `<meta name="description">` from project description, Open Graph (`og:title`, `og:description`, `og:type=website`, `og:url`), `<link rel="alternate" type="application/atom+xml" href=".../feed.atom">`, `<link rel="alternate" type="application/rss+xml" href=".../feed.rss">`, `<style>` block with branding primary color from `project.branding.primaryColor` (fallback `#4F46E5`).
- `<body>` — `<header>` with project name + logo, `<main>` containing one `<article id="post-{uuid}">` per published post (title as `<h2>`, `<time datetime="ISO-8601">`, summary, rendered markdown content via `commonmark-java` — see Implementation step 6), `<footer>` with "Subscribe via Atom / RSS / Email" links. Empty state: `<p>No releases yet — check back soon.</p>`.
- 404 page: same template shell with `<h1>Not found</h1>`.

Errors: 404 renders a small Thymeleaf 404 template (no stack trace leakage).

## Architecture & data flow

```
 Public HTTP (no auth)
 │
 ├─ GET /changelog/{slug}/feed.atom ─┐
 ├─ GET /changelog/{slug}/feed.rss  ─┼──▶ FeedController (new, replaces RssFeedController)
 ├─ GET /changelog/{slug}/feed.xml  ─┘     │
 │                                         ├─▶ ProjectService.getProjectBySlug(slug)     ──▶ projects table
 │                                         └─▶ PostRepository.findByProjectIdAndStatus…  ──▶ changelog_posts
 │                                                                                           (status='PUBLISHED')
 │                                         Rome SyndFeed builder (atom_1.0 | rss_2.0)
 │                                         → String XML → ResponseEntity with proper media-type
 │
 ├─ GET /changelog/{slug}/search?q=… ─▶ PublicSearchController (new)
 │                                         │
 │                                         ├─▶ ProjectService.getProjectBySlug
 │                                         └─▶ PostRepository.searchByProject (existing native query)
 │                                            returns List<Object[]> [post, rank]
 │                                            → mapped to SearchResultResponse
 │
 └─ GET /p/{slug} ─────────────────────▶ PublicHtmlController (new, @Controller not @RestController)
                                           │
                                           ├─▶ ProjectService.getProjectBySlug
                                           ├─▶ PostService.getPublishedPosts(projectId)
                                           ├─▶ MarkdownRenderer.toHtml(post.content)
                                           └─▶ Thymeleaf view "public/changelog"
                                              → rendered HTML
```

**Why Thymeleaf over a hand-rolled string template.**
- Spring Boot ships a first-class Thymeleaf starter (`spring-boot-starter-thymeleaf`) — zero config beyond adding the dep; `templates/` dir already exists empty.
- Auto-escapes by default: prevents XSS when posting user-supplied markdown (we still render content HTML via `th:utext` but only after passing it through a sanitizer).
- Built-in i18n, fragment composition for shared `<head>`, and a 404 error template via `resolveErrorView`.
- Alternatives considered: Mustache (less ecosystem, no error-view hook), Freemarker (heavier, worse IDE tooling), raw `String.format` (no escaping, brittle). Thymeleaf wins on safety + zero-friction Spring integration.

**Markdown rendering.** Posts store Markdown in `changelog_posts.content` (`V1__init.sql:62`). Introduce `commonmark-java` 0.22.0 as a dependency (CommonMark spec, sanitizes unsafe HTML via its `HtmlRenderer` config `escapeHtml=true`). Render on the server; the resulting HTML is passed to Thymeleaf via `th:utext`. No other part of the codebase currently depends on this library.

**Slug → tenant scoping.** `ProjectService.getProjectBySlug(slug)` (`ProjectService.java:46`) uses `projectRepository.findBySlug(slug)` which is globally unique only by convention (the DB only enforces uniqueness on `(tenant_id, slug)` — `V1__init.sql:45`). For the public surface this is acceptable today because V2 seeds a single demo tenant; a follow-up plan will add host-based tenant resolution. This plan does not widen that attack surface.

## Database changes

**None.** V1 already provides:

- `changelog_posts.content_tsv` — generated `tsvector` column (`V1__init.sql:63-64`).
- `idx_changelog_posts_tsv` — `GIN(content_tsv)` (`V1__init.sql:75`).
- `idx_changelog_posts_published` — supports feed ordering (`V1__init.sql:77`).

Because the generated column and index are not mapped in `Post.java` and we won't add any new columns, Hibernate's `ddl-auto: validate` (`application.yml:13`) will not complain, and `SchemaValidationIT` remains green. We verified `content_tsv` is absent from `Post.java:25-81` (intentional — it's server-maintained).

No new `V5__…sql` migration is created. If a future iteration wants a dedicated `search_posts()` function or to index `summary`, that will be a separate plan.

## Files to create or modify

### `com.changelog.controller`
| path | new/modify | purpose |
|---|---|---|
| `src/main/java/com/changelog/controller/FeedController.java` | new | Replaces `RssFeedController`. Handles `/feed.atom`, `/feed.rss`, and a 301 from `/feed.xml`. |
| `src/main/java/com/changelog/controller/RssFeedController.java` | delete | Superseded by `FeedController`. Its one endpoint moves and becomes format-aware. |
| `src/main/java/com/changelog/controller/PublicSearchController.java` | new | `GET /changelog/{slug}/search`. Kept out of `PublicChangelogController` to avoid growing that class past ~200 lines. |
| `src/main/java/com/changelog/controller/PublicHtmlController.java` | new | `GET /p/{slug}` — returns `String` (view name), not `@RestController`. Annotated `@Controller`. |
| `src/main/java/com/changelog/controller/PublicChangelogController.java` | unchanged | Keep existing JSON endpoints. |
| `src/main/java/com/changelog/controller/PublicController.java` | unchanged | Keep `/p/{tenantSlug}/{projectSlug}` JSON endpoint (backwards compat). |

### `com.changelog.dto`
| path | new/modify | purpose |
|---|---|---|
| `src/main/java/com/changelog/dto/SearchResultResponse.java` | new | Wraps `{query, total, limit, results}` and an inner `SearchHit {id,title,summary,publishedAt,rank}`. Re-adds the type whose import was removed from `PublicChangelogController.java:25`. |

### `com.changelog.repository`
| path | new/modify | purpose |
|---|---|---|
| `src/main/java/com/changelog/repository/PostRepository.java` | modify | Rename `searchPublishedPostsByProject` to `searchPublishedPostsByProject` (already exists at line 40-48) — no change in signature. Add a `countByProjectIdAndStatus` helper only if simpler to derive `total` from a separate count; otherwise take `results.size()`. |

### `com.changelog.service`
| path | new/modify | purpose |
|---|---|---|
| `src/main/java/com/changelog/service/PostSearchService.java` | new | Thin wrapper that calls `PostRepository.searchPublishedPostsByProject`, maps `Object[]` rows to `SearchResultResponse.SearchHit`, applies `limit`. Keeps controller dumb. |
| `src/main/java/com/changelog/service/MarkdownRenderer.java` | new | Wraps `commonmark-java` parser + HTML renderer with safe defaults. Singleton bean (thread-safe). |

### `com.changelog.config`
| path | new/modify | purpose |
|---|---|---|
| `src/main/java/com/changelog/config/PublicUrlProperties.java` | new (optional) | `@ConfigurationProperties(prefix = "app")` — typesafe access to `public-url` so controllers don't each re-read `@Value`. Optional but recommended; if skipped, continue using `@Value("${app.public-url}")` as in `RssFeedController.java:30`. |

### `src/main/resources`
| path | new/modify | purpose |
|---|---|---|
| `src/main/resources/templates/public/changelog.html` | new | Main HTML view for `/p/{slug}`. Fragments for `<head>`, branding CSS, post `<article>`. |
| `src/main/resources/templates/public/404.html` | new | Rendered 404 body for unknown slug. |
| `src/main/resources/static/public.css` | new | Minimal CSS (system font stack, ~100 lines). Linked from `changelog.html`. Keeps template readable; keeps branding color as an inline CSS variable set from the server. |

### `pom.xml`
| path | new/modify | purpose |
|---|---|---|
| `pom.xml` | modify | Add `spring-boot-starter-thymeleaf` (managed version from parent 3.3.5). Add `org.commonmark:commonmark:0.22.0`. Do not touch Lombok (`1.18.44`) or testcontainers. |

### Tests (`src/test/java`)
| path | new/modify | purpose |
|---|---|---|
| `src/test/java/com/changelog/controller/FeedControllerIT.java` | new | Spring MVC test: `/feed.atom` and `/feed.rss` return valid XML, correct content types. |
| `src/test/java/com/changelog/controller/PublicSearchControllerIT.java` | new | 400 on missing `q`, 404 on unknown slug, 200 with ranked hits for known slug+query. |
| `src/test/java/com/changelog/controller/PublicHtmlControllerIT.java` | new | Assert `<!doctype html>`, one `<article>` per post, `<link rel="alternate" type="application/atom+xml">`, Open Graph meta, 404 path. |
| `src/test/java/com/changelog/service/MarkdownRendererTest.java` | new | Unit test: headings render; raw `<script>` is escaped; empty/null input returns empty string. |
| `src/test/java/com/changelog/SchemaValidationIT.java` | unchanged | Must stay green. |

## Implementation steps

Each step is ≤ 1 commit. Steps 1–3 are preparation; 4–6 ship search; 7–10 ship feeds; 11–14 ship HTML.

1. **Add dependencies.** Modify `pom.xml`: add `spring-boot-starter-thymeleaf` and `org.commonmark:commonmark:0.22.0`. Run `mvn -q compile` and `mvn -q test -Dtest=SchemaValidationIT` to confirm nothing breaks. No code yet.

2. **Introduce `SearchResultResponse`.** Create `src/main/java/com/changelog/dto/SearchResultResponse.java` with Lombok `@Data`/`@Builder`/`@NoArgsConstructor`/`@AllArgsConstructor` plus a static nested `SearchHit` record-style class. This restores the symbol whose import was removed from `PublicChangelogController.java:25`.

3. **Introduce `PostSearchService`.** Thin service that:
   - Accepts `(UUID projectId, String query, int limit)`.
   - Calls the existing `PostRepository.searchPublishedPostsByProject` (`PostRepository.java:40-48`) — note it returns `List<Object[]>` where `[0]=Post`, `[1]=Double rank`.
   - Maps to `SearchHit`, truncates to `limit`, returns `SearchResultResponse`.
   - No schema or entity changes.

4. **Wire search controller.** Create `PublicSearchController` at `/changelog/{slug}/search`:
   - Validates `q` (not blank, ≤ 200 chars) via `@RequestParam @NotBlank @Size(max=200)` or manual check returning `400`.
   - Validates `limit` (1..50, default 20).
   - Resolves project via `ProjectService.getProjectBySlug(slug)`; catches `EntityNotFoundException` → 404.
   - Delegates to `PostSearchService`; returns JSON.

5. **Add a `@ControllerAdvice` (or local `@ExceptionHandler`) that produces the `{"error":"..."}` JSON body for `IllegalArgumentException` / `MethodArgumentNotValidException`** scoped to `PublicSearchController` only (don't change global behaviour). Simplest path: inline handler methods on the controller class.

6. **Integration test for search.** Write `PublicSearchControllerIT` that: (a) boots with `@SpringBootTest(webEnvironment=MOCK) + @AutoConfigureMockMvc`, (b) uses the V2 seeded `demo-project` tenant and demo posts, (c) asserts the four cases in Acceptance criteria 4–6. Run against the already-running docker-compose Postgres, same way `SchemaValidationIT` does — copy its `@TestPropertySource` block.

7. **Delete `RssFeedController.java` and create `FeedController.java`.** The new controller has three handlers:
   - `GET /changelog/{slug}/feed.atom` → `buildFeed(slug, "atom_1.0")` → `application/atom+xml;charset=UTF-8`.
   - `GET /changelog/{slug}/feed.rss` → `buildFeed(slug, "rss_2.0")` → `application/rss+xml;charset=UTF-8`.
   - `GET /changelog/{slug}/feed.xml` → `ResponseEntity.status(301).location(feedRssUri).build()`.
   - Extract the Rome-specific logic out of the old class (`RssFeedController.java:45-77`). Add `<link rel="self">` for Atom (Rome's `SyndFeed.setLinks(...)` with `rel="self"` and the absolute URL).
   - Keep markdown-rendered HTML in Atom `<content type="html">` via `MarkdownRenderer`; keep plain-text summary in `<summary>`. For RSS, put plain `summary` in `<description>` (matches existing behaviour at `RssFeedController.java:63-66`).

8. **Permalink strategy.** Each entry's `<link rel="alternate">` points to `{public-url}/p/{slug}#post-{postId}`. That matches the HTML page's `<article id="post-{uuid}">` anchors. Entry `<id>` is `urn:uuid:{postId}` (stable across URL changes).

9. **Integration test for feeds.** `FeedControllerIT` parses both responses with `javax.xml.parsers.DocumentBuilder` and asserts: root element name, child counts (== # published posts in V2), content-type header, 404 for unknown slug, 301 for `/feed.xml`.

10. **Update `README.md` "API Endpoints > Public"** to list `/feed.atom`, `/feed.rss`, `/feed.xml (301)`, `/search`, `/p/{slug}` — replacing the "RSS feed — Not started" row in the Current State table. (Doc-only change, separate small commit.)

11. **Create `MarkdownRenderer` + unit tests.** Bean with `commonmark-java` `Parser.builder().build()` + `HtmlRenderer.builder().escapeHtml(true).build()`. Method: `String toHtml(String markdown)`. Test empty, plain text, headings, and a `<script>` injection attempt. Used by both `FeedController` (Atom content) and `PublicHtmlController` (article body).

12. **Thymeleaf templates.**
   - `templates/public/changelog.html` — HTML5 doctype, `<html lang="en">`, branding color via inline `<style>:root { --primary: [[${primaryColor}]]; }</style>`, `<main>` iterating `${posts}` with `th:each`. Use `th:utext="${post.renderedContent}"` for HTML (already sanitized by MarkdownRenderer); `th:text` everywhere else.
   - `templates/public/404.html` — small shell, same `<head>` fragment.
   - `static/public.css` — ~100 lines. Keep the template self-contained so it works offline / in CI.

13. **PublicHtmlController.** `@Controller` (not `@RestController`), method returns the template name `"public/changelog"` and loads the model:
   ```
   model.addAttribute("project", project);        // ProjectResponse
   model.addAttribute("posts", renderedPosts);    // List<RenderedPost> — DTO with pre-rendered HTML + date
   model.addAttribute("primaryColor", project.getBranding().getOrDefault("primaryColor", "#4F46E5"));
   model.addAttribute("feedAtomUrl", publicUrl + "/changelog/" + slug + "/feed.atom");
   model.addAttribute("feedRssUrl",  publicUrl + "/changelog/" + slug + "/feed.rss");
   ```
   On `EntityNotFoundException` return `ResponseEntity.status(404).body(...)` via an `@ExceptionHandler` returning the `public/404` template.

14. **Integration test for HTML.** `PublicHtmlControllerIT`:
   - Boots MockMvc.
   - `GET /p/demo-project` → 200, `Content-Type: text/html;charset=UTF-8`, body matches regex `(?i)<!doctype html>`, contains one `<article` per published post, contains `<link rel="alternate" type="application/atom+xml"`, contains `<meta property="og:title"`.
   - `GET /p/does-not-exist` → 404, body contains `<h1>Not found</h1>`.

15. **Final smoke + docs.** Run `mvn -q verify`; run the manual curl block in the Test plan below against `localhost:8081`; update `README.md` "Current State" table rows for RSS/search/public HTML from "Not started" to "Done".

## Test plan

### Unit tests

| Class | Behaviour asserted |
|---|---|
| `MarkdownRendererTest` | `toHtml("# hi")` contains `<h1>hi</h1>`; `toHtml(null)` returns `""`; `toHtml("<script>x</script>")` does not contain raw `<script>` (HTML-escaped). |
| `PostSearchServiceTest` | Mock `PostRepository`; verify `Object[]` mapping, `limit` truncation, empty result returns `total=0`. |
| `SearchResultResponseTest` | Round-trip through Jackson (matches the JSON shape in Acceptance criterion 4). |

### Integration tests (boot full app against local Postgres 5433, same pattern as `SchemaValidationIT.java:38-43`)

| Class | Scenarios |
|---|---|
| `FeedControllerIT` | (a) `/feed.atom` → 200 + Atom namespace + N entries; (b) `/feed.rss` → 200 + `<rss version="2.0">` + N items; (c) `/feed.xml` → 301 to `/feed.rss`; (d) unknown slug → 404; (e) Atom entry ids match `urn:uuid:{postId}`. |
| `PublicSearchControllerIT` | (a) `?q=feature` → 200 with ranked results; (b) `?q=` → 400; (c) missing `q` → 400; (d) `?q=...&limit=100` → 400; (e) `/unknown-slug/search?q=x` → 404. |
| `PublicHtmlControllerIT` | (a) Known slug → 200, HTML5 doctype, one `<article>` per published post, alternate link tags, Open Graph meta; (b) Unknown slug → 404 HTML; (c) Project with zero posts → 200 with empty-state text. |
| `SchemaValidationIT` (existing) | Must still pass — no migration was added, so this is automatic. Run it in CI to prove ddl-auto=validate still likes every entity. |

### Manual smoke (proves each AC by curl)

```bash
# 0. boot
docker compose up -d postgres
mvn -q spring-boot:run -Dspring-boot.run.profiles=local &
sleep 8

BASE=http://localhost:8081
SLUG=demo-project   # seeded by V2__sample_data.sql

# AC 1 — Atom feed validates
curl -sS "$BASE/changelog/$SLUG/feed.atom" | xmllint --noout -
curl -sS "$BASE/changelog/$SLUG/feed.atom" | xmllint --xpath '//*[local-name()="feed"]/*[local-name()="title"]/text()' -

# AC 2 — RSS 2.0
curl -sS "$BASE/changelog/$SLUG/feed.rss" | xmllint --xpath 'string(/rss/@version)' -

# AC 3 — content types
curl -sSI "$BASE/changelog/$SLUG/feed.atom" | grep -i '^content-type:'
curl -sSI "$BASE/changelog/$SLUG/feed.rss"  | grep -i '^content-type:'
curl -sS  -o /dev/null -w '%{http_code}\n' "$BASE/changelog/does-not-exist/feed.atom"   # → 404

# AC 4 — search
curl -sS "$BASE/changelog/$SLUG/search?q=feature" | jq .

# AC 5 — 400 on missing q
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/changelog/$SLUG/search"      # → 400
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/changelog/$SLUG/search?q="   # → 400

# AC 6 — 404 on unknown slug
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/changelog/nope/search?q=x"   # → 404

# AC 7, 8 — HTML page
curl -sS "$BASE/p/$SLUG" | head -1                            # <!doctype html>
curl -sS "$BASE/p/$SLUG" | grep -c '<article'                 # == N published posts
curl -sS "$BASE/p/$SLUG" | grep -c 'rel="alternate" type="application/atom+xml"'  # 1
curl -sS "$BASE/p/$SLUG" | grep -c 'property="og:title"'      # 1

# AC 9 — zero-posts path (create an empty project first via authed API)
# (not strictly needed for smoke — covered by integration test)

# AC 10 — 404 HTML
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/p/does-not-exist"   # → 404

# AC 11 — schema IT still green
mvn -q -pl . test -Dtest=SchemaValidationIT

# AC 12 — tsv query plan
psql -h localhost -p 5433 -U changelog -d changelog -c "EXPLAIN ANALYZE \
  SELECT p.id, ts_rank(p.content_tsv, plainto_tsquery('english','feature')) \
  FROM changelog_posts p \
  WHERE p.status='PUBLISHED' \
    AND p.content_tsv @@ plainto_tsquery('english','feature');"
# Expect 'Bitmap Index Scan on idx_changelog_posts_tsv' in the plan.
```

## Risks & open questions

1. **`ProjectRepository.findBySlug` is globally unambiguous only while there's a single demo tenant.** `V1__init.sql:45` enforces uniqueness on `(tenant_id, slug)`, not `slug` alone. If two tenants ever pick the same slug, `findBySlug(slug)` (invoked from feeds, search, and the HTML page via `ProjectService.java:46`) becomes non-deterministic. Out of scope for this plan, but call out: a follow-up must add host-based tenant resolution or a platform-wide unique slug constraint.
2. **Markdown injection.** Post content is editor-authored Markdown stored as-is. `commonmark-java` with `escapeHtml=true` strips raw HTML inside the Markdown, but we still pass the resulting HTML to Thymeleaf via `th:utext` (unescaped). The combination is safe as long as `HtmlRenderer` is configured with `escapeHtml(true)` — the unit test in step 11 pins that invariant.
3. **Rome character encoding.** Rome can silently emit `ISO-8859-1` if the writer default kicks in. Set `SyndFeed.setEncoding("UTF-8")` explicitly and write via `SyndFeedOutput.outputString(feed)` then wrap in a `Content-Type` header that includes `;charset=UTF-8` (the old `RssFeedController.java:78-80` already does this — keep the pattern).
4. **Post HTML in Atom `<content type="html">`.** Some readers (NetNewsWire) strip `<script>` aggressively; we already escape server-side but should test at least one real reader. Optional: add a human-smoke note in AC.
5. **Feed pagination.** Not included. A project with 5000 posts produces a 2–5 MB feed XML. Out of scope but noted — a follow-up should cap entries to the last 50 or add RFC 5005 paging.
6. **`publishedAt` is `LocalDateTime`, not `OffsetDateTime`.** The existing `Post.java:54` stores `LocalDateTime publishedAt`, and `RssFeedController.java:60` uses `ZoneId.systemDefault()` to convert. Atom and RSS want timezone-qualified timestamps; this will produce wrong UTC times in containers whose timezone is not UTC. Mitigation: always run the app in `TZ=UTC` (already the default in Docker images), and add a TODO to convert the column to `TIMESTAMP WITH TIME ZONE` in a future plan.
7. **Caching.** No `Cache-Control` headers in v1. Feeds and HTML will be re-rendered on every request. At the demo-project scale this is fine (< 20 posts); at 10k posts per project we will want to add `ETag` / `If-None-Match`. Out of scope here; noted for the 10k-post AC check.
8. **Thymeleaf dev reload.** With `spring-boot-devtools` (not currently in `pom.xml:40-159`) templates would hot-reload. We explicitly don't add devtools to production dependencies; developers can run `mvn spring-boot:run` to pick up template changes on restart.
9. **Search multi-language.** `content_tsv` is built with `to_tsvector('english', …)` (`V1__init.sql:64`). Projects whose content is Spanish/Japanese will see weaker ranking. Accepted limitation; documented in the response model's `rank` field (no claim of language-awareness).
10. **Open question:** should `/p/{slug}` redirect to `/p/{tenantSlug}/{projectSlug}` (the existing `PublicController` route) once host-based tenant resolution exists? Decision deferred. For now, the two routes coexist; the new `/p/{slug}` is the customer-facing share URL.

## Definition of done

- [ ] All 12 acceptance criteria verifiably pass on a fresh `docker compose up -d postgres && mvn -Dspring-boot.run.profiles=local spring-boot:run` environment.
- [ ] `mvn -q verify` green, including:
  - `SchemaValidationIT` (unchanged).
  - New `FeedControllerIT`, `PublicSearchControllerIT`, `PublicHtmlControllerIT`.
  - New `MarkdownRendererTest`, `PostSearchServiceTest`.
- [ ] `RssFeedController.java` deleted; `FeedController.java` merged; `/feed.xml` still reachable via 301 to `/feed.rss`.
- [ ] `PublicChangelogController.java` unchanged except for any cleanup of the already-missing `SearchResultResponse` import reference — no behavioural change to `/changelog/{slug}`, `/posts`, `/subscribe`, `/unsubscribe`.
- [ ] `pom.xml` has new `spring-boot-starter-thymeleaf` and `org.commonmark:commonmark:0.22.0`; no version of Lombok, Rome, or Spring Boot changed.
- [ ] `README.md` "Current State" table: RSS feed / full-text search / HTML page rows flipped from "Not started" → "Done", with endpoint shapes listed under "API Endpoints > Public".
- [ ] Manual curl block from the Test plan runs cleanly end-to-end against `localhost:8081`.
- [ ] `EXPLAIN ANALYZE` against `content_tsv` shows `Bitmap Index Scan on idx_changelog_posts_tsv` (proves the GIN index is in use, AC 12).
- [ ] No new Flyway migration file exists (this plan was explicit: zero schema changes).
