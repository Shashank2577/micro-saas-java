# Autopilot Spec — Changelog Platform Next Sprint

**Project:** changelog-platform (Spring Boot 3.3.5, Java 21, PostgreSQL 16)  
**Working dir:** `changelog-platform/`  
**Date:** 2026-04-16

---

## What's Already Built (DO NOT re-implement)

- Projects, Posts (Draft/Scheduled/Published), Tags, Subscribers, WidgetConfig — full JPA entities
- `PostService.publishPost()` — sets status=PUBLISHED, publishedAt=now, saves
- `PostRepository.findScheduledPostsDueForPublishing(LocalDateTime now)` — JPQL query already written
- `SubscriberRepository.findByProjectId()`, `findByProjectIdAndStatus()` — ready to use
- `DripCampaignService` with `@Scheduled(cron = "0 0 * * * *")` — exact scheduling pattern to follow
- `ChangelogPlatformApplication` has `@EnableScheduling` and `@EnableJpaAuditing`
- `spring-boot-starter-mail` is **already in pom.xml** — do NOT add it again
- `PublicChangelogController` at `/changelog/{slug}` — add RSS and subscribe endpoints here
- `TenantResolver` interface + `LocalTenantResolver`/`JwtTenantResolver` — auth pattern
- `SecurityConfigLocal` — permits all for `local` profile

---

## Features to Build

### Feature 1: Scheduled Publishing Job

**New file:** `src/main/java/com/changelog/service/ScheduledPublishingJob.java`

A Spring `@Component` with an `@Scheduled` job that runs every minute, finds posts whose `scheduledFor <= now()` with status `SCHEDULED`, and publishes them.

**Logic:**
1. Call `postRepository.findScheduledPostsDueForPublishing(LocalDateTime.now())`
2. For each post: set `status = PUBLISHED`, `publishedAt = now`, save via `postRepository.save(post)`
3. After saving, call `subscriberNotificationService.notifySubscribers(post)` to email subscribers
4. Log each published post: `log.info("Scheduled post published: id={} title='{}' project={}", ...)`
5. Schedule: `@Scheduled(cron = "0 * * * * *")` (every minute, not every hour)

**Dependencies to inject:** `PostRepository`, `SubscriberNotificationService`

---

### Feature 2: Subscriber Email Notification Service

**New file:** `src/main/java/com/changelog/service/SubscriberNotificationService.java`

Sends email to all active subscribers of a project when a post is published.

**Logic:**
1. Method signature: `public void notifySubscribers(Post post)`
2. Load the project via `ProjectRepository.findById(post.getProjectId())`
3. Find all active subscribers: `subscriberRepository.findByProjectIdAndStatus(post.getProjectId(), Subscriber.SubscriberStatus.ACTIVE, Pageable.unpaged())` — or use `findByProjectId` and filter in Java
4. For each subscriber, build a `SimpleMailMessage`:
   - `from`: from config `app.notification-email` (default: `noreply@changelog-platform.io`)
   - `to`: subscriber email
   - `subject`: `"[{projectName}] {post.title}"`
   - `text`: plain-text body with title, summary, and a link to `{app.public-url}/changelog/{project.slug}/{post.id}`
5. Call `mailSender.send(message)` wrapped in try/catch — log failure but don't throw
6. Log: `log.info("Sent notification to {} subscribers for post '{}'", count, post.getTitle())`

**Also update `PostService.publishPost()`:** after `postRepository.save(updated)`, call `subscriberNotificationService.notifySubscribers(updated)`. Use `@Lazy` injection on `SubscriberNotificationService` in `PostService` to avoid any circular dependency risk.

**Config to add to `application.yml`** (under existing keys):
```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

app:
  notification-email: ${NOTIFICATION_EMAIL:noreply@changelog-platform.io}
```

**Config to add to `application-local.yml`** (disable actual sending in local dev):
```yaml
spring:
  mail:
    host: localhost
    port: 1025
    username: test
    password: test
    properties:
      mail:
        smtp:
          auth: false
          starttls:
            enable: false

app:
  notification-email: noreply@localhost
```

The local profile runs without a real SMTP server — if connection fails the catch block logs it and continues. This is intentional for local dev.

---

### Feature 3: RSS Feed Endpoint

**New dependency to add to `pom.xml`:**
```xml
<dependency>
    <groupId>com.rometools</groupId>
    <artifactId>rome</artifactId>
    <version>2.1.0</version>
</dependency>
```

**New file:** `src/main/java/com/changelog/controller/RssFeedController.java`

```
GET /changelog/{slug}/feed.xml
Produces: application/rss+xml;charset=UTF-8
```

**Logic:**
1. Look up project by slug via `ProjectService.getProjectBySlug(slug)` — return 404 if not found
2. Fetch published posts: `postRepository.findByProjectIdAndStatusOrderByPublishedAtDesc(project.getId(), Post.PostStatus.PUBLISHED)`
3. Build `SyndFeed` (Rome library):
   - `feedType = "rss_2.0"`
   - `title = project.getName() + " Changelog"`
   - `description = project.getDescription()`
   - `link = appPublicUrl + "/changelog/" + slug`
   - For each post, build `SyndEntry`:
     - `title = post.getTitle()`
     - `description` = `SyndContent` with `post.getSummary()` (or first 200 chars of content)
     - `link = appPublicUrl + "/changelog/" + slug + "/" + post.getId()`
     - `uri = post.getId().toString()` (guid)
     - `publishedDate = Date.from(post.getPublishedAt().toInstant(ZoneOffset.UTC))`
4. Output via `SyndFeedOutput().outputString(feed)` wrapped in try/catch `FeedException`
5. Return `ResponseEntity.ok().contentType(MediaType.parseMediaType("application/rss+xml;charset=UTF-8")).body(xml)`

**Inject:** `ProjectService`, `PostRepository`, and `@Value("${app.public-url}") String appPublicUrl`

---

### Feature 4: Public Subscribe / Unsubscribe Endpoints

Add to **existing** `PublicChangelogController`:

**Subscribe:**
```
POST /changelog/{slug}/subscribe
Body: {"email": "user@example.com", "name": "Jane Doe"}
Response: 200 OK or 409 if already subscribed
```
- Look up project by slug
- Check `subscriberRepository.existsByProjectIdAndEmail(project.getId(), email)`
- If exists and ACTIVE: return 409
- If exists and UNSUBSCRIBED: re-activate (set status=ACTIVE, unsubscribedAt=null, save)
- If new: create Subscriber entity and save
- No email confirmation for MVP (subscribe immediately)

**Unsubscribe:**
```
POST /changelog/{slug}/unsubscribe
Body: {"email": "user@example.com"}
Response: 200 OK (always, even if email not found — prevents email enumeration)
```
- Look up project by slug
- Find subscriber by projectId + email
- If found and ACTIVE: set status=UNSUBSCRIBED, unsubscribedAt=now, save
- Always return 200

**New DTOs needed:**
- `src/main/java/com/changelog/dto/SubscribeRequest.java` — `@NotBlank String email`, `String name`
- Both endpoints are unauthenticated (public) — already handled by `SecurityConfigLocal` and the permit-all pattern

---

## Constraints

- **Package:** all new files under `com.changelog.*` matching existing package structure
- **No new Flyway migrations** — all schema changes already exist in V1–V4
- **No new Spring profiles** — use existing `local` and default profiles
- **Lombok everywhere** — `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`
- **Java 21** (pom.xml sets `java.version=21`)
- **Transactional:** `@Transactional` on service write methods
- **Error handling:** catch exceptions in notification service; never let email failure break the publish flow
