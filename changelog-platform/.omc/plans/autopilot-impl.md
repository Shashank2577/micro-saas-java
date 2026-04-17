# Implementation Plan — Changelog Platform Next Sprint

## Order of execution (some steps can be parallel)

### Step 1 — Add Rome RSS dependency (pom.xml)
- Add `com.rometools:rome:2.1.0` to pom.xml dependencies
- Verify `spring-boot-starter-mail` is NOT added again (already present)

### Step 2 — Add mail config to application.yml + application-local.yml
- Add `spring.mail.*` block to `application.yml` with env-var-backed defaults
- Add `app.notification-email` to `application.yml`
- Add local-dev mail override to `application-local.yml` (localhost:1025, no auth)

### Step 3 — Create SubscribeRequest DTO
- `src/main/java/com/changelog/dto/SubscribeRequest.java`
- Fields: `@NotBlank String email`, `String name`
- `@Data @NoArgsConstructor @AllArgsConstructor`

### Step 4 — Create SubscriberNotificationService
- `src/main/java/com/changelog/service/SubscriberNotificationService.java`
- Depends on: `SubscriberRepository`, `ProjectRepository`, `JavaMailSender`
- `@Value("${app.public-url}") String publicUrl`
- `@Value("${app.notification-email}") String fromEmail`
- Method: `notifySubscribers(Post post)`
- Catch all mail exceptions, log, continue

### Step 5 — Update PostService to notify on publish
- Inject `SubscriberNotificationService` with `@Lazy` to avoid circular dependency
- At end of `publishPost()`, after save: call `subscriberNotificationService.notifySubscribers(updated)`
- Do NOT change method signature or return type

### Step 6 — Create ScheduledPublishingJob
- `src/main/java/com/changelog/service/ScheduledPublishingJob.java`
- `@Scheduled(cron = "0 * * * * *")`
- Query: `postRepository.findScheduledPostsDueForPublishing(LocalDateTime.now())`
- For each post: set PUBLISHED + publishedAt, save, then call notifySubscribers

### Step 7 — Create RssFeedController
- `src/main/java/com/changelog/controller/RssFeedController.java`
- `GET /changelog/{slug}/feed.xml` → `application/rss+xml`
- Uses Rome `SyndFeed`, `SyndEntry`, `SyndFeedOutput`

### Step 8 — Add subscribe/unsubscribe endpoints to PublicChangelogController
- `POST /changelog/{slug}/subscribe` — create or re-activate subscriber
- `POST /changelog/{slug}/unsubscribe` — soft-delete (set status=UNSUBSCRIBED)

### Step 9 — Verify build compiles
- Run `mvn compile -q` and confirm zero errors
- Run `mvn spring-boot:run -Dspring-boot.run.profiles=local` and confirm startup

## Files to create (new)
1. `src/main/java/com/changelog/dto/SubscribeRequest.java`
2. `src/main/java/com/changelog/service/SubscriberNotificationService.java`
3. `src/main/java/com/changelog/service/ScheduledPublishingJob.java`
4. `src/main/java/com/changelog/controller/RssFeedController.java`

## Files to modify (existing)
1. `pom.xml` — add Rome dependency
2. `src/main/resources/application.yml` — add mail + notification-email config
3. `src/main/resources/application-local.yml` — add local mail override
4. `src/main/java/com/changelog/service/PostService.java` — call notifySubscribers after publish
5. `src/main/java/com/changelog/controller/PublicChangelogController.java` — add subscribe/unsubscribe
