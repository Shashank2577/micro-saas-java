# Jules Task: Changelog Platform — Next Sprint Features

## Context

This is a Spring Boot 3.3.5 / Java 21 / PostgreSQL 16 application.  
Working directory: `changelog-platform/`  
Package root: `com.changelog`

The backend is fully working and booting. The task is to implement 4 features from the next sprint roadmap.

---

## What Already Exists (DO NOT re-implement or duplicate)

### Dependencies already in pom.xml
- `spring-boot-starter-mail` — already present, do NOT add again
- `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`
- `spring-boot-starter-validation`, `postgresql`, `flyway-core`, Lombok

### Entities (src/main/java/com/changelog/model/)
- `Post.java` — has fields: `id`, `projectId`, `tenantId`, `title`, `summary`, `content`, `status` (DRAFT/SCHEDULED/PUBLISHED), `publishedAt`, `scheduledFor`, `viewCount`, `tags`
- `Project.java` — has `id`, `tenantId`, `name`, `slug`, `description`
- `Subscriber.java` — has `id`, `projectId`, `email`, `name`, `planTier`, `status` (ACTIVE/UNSUBSCRIBED), `subscribedAt`, `unsubscribedAt`

### Repositories (src/main/java/com/changelog/repository/)
- `PostRepository` — already has `findScheduledPostsDueForPublishing(LocalDateTime now)` JPQL query
- `PostRepository` — already has `findByProjectIdAndStatusOrderByPublishedAtDesc(UUID, PostStatus)`
- `SubscriberRepository` — already has `findByProjectId(UUID)`, `findByProjectIdAndStatus(UUID, SubscriberStatus, Pageable)`, `existsByProjectIdAndEmail(UUID, String)`, `findByProjectIdAndEmail(UUID, String)`
- `ProjectRepository` — standard JPA, `findById`, `findBySlug` (check if exists or add)

### Services
- `PostService.publishPost(UUID tenantId, UUID postId)` — sets PUBLISHED, saves. **Modify this to call notification service.**
- `ProjectService.getProjectBySlug(String slug)` — already exists, throws EntityNotFoundException if not found

### Controllers
- `PublicChangelogController` at `/changelog/{slug}` — **add subscribe/unsubscribe endpoints here**
- `ApiV1Controller` — authenticated endpoints, leave untouched

### Infrastructure
- `ChangelogPlatformApplication` — already has `@EnableScheduling` and `@EnableJpaAuditing`
- `SecurityConfigLocal` (`@Profile("local")`) — permits all requests, no auth needed
- `TenantResolver` interface with `LocalTenantResolver` (local) and `JwtTenantResolver` (prod)
- `DripCampaignService` — has working `@Scheduled(cron = "0 0 * * * *")` example to follow

---

## Task 1: Add Rome RSS Library Dependency

**File:** `pom.xml`

Add inside `<dependencies>`:
```xml
<dependency>
    <groupId>com.rometools</groupId>
    <artifactId>rome</artifactId>
    <version>2.1.0</version>
</dependency>
```

---

## Task 2: Add Mail Configuration

**File:** `src/main/resources/application.yml`

Add these blocks (merge with existing, do not duplicate existing keys):
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
  public-url: ${PUBLIC_URL:http://localhost:8080}
  notification-email: ${NOTIFICATION_EMAIL:noreply@changelog-platform.io}
```

**File:** `src/main/resources/application-local.yml`

Add these blocks (merge with existing):
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

Note: The local profile will try localhost:1025 — connection failures are caught and logged, not thrown. This is intentional for local dev without a real SMTP server.

---

## Task 3: Create SubscribeRequest DTO

**New file:** `src/main/java/com/changelog/dto/SubscribeRequest.java`

```java
package com.changelog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeRequest {

    @NotBlank
    @Email
    private String email;

    private String name;
}
```

---

## Task 4: Create SubscriberNotificationService

**New file:** `src/main/java/com/changelog/service/SubscriberNotificationService.java`

```java
package com.changelog.service;

import com.changelog.model.Post;
import com.changelog.model.Project;
import com.changelog.model.Subscriber;
import com.changelog.repository.ProjectRepository;
import com.changelog.repository.SubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriberNotificationService {

    private final SubscriberRepository subscriberRepository;
    private final ProjectRepository projectRepository;
    private final JavaMailSender mailSender;

    @Value("${app.public-url:http://localhost:8080}")
    private String publicUrl;

    @Value("${app.notification-email:noreply@changelog-platform.io}")
    private String fromEmail;

    public void notifySubscribers(Post post) {
        Project project = projectRepository.findById(post.getProjectId()).orElse(null);
        if (project == null) {
            log.warn("Project {} not found for post {}, skipping notifications", post.getProjectId(), post.getId());
            return;
        }

        List<Subscriber> activeSubscribers = subscriberRepository.findByProjectId(post.getProjectId())
                .stream()
                .filter(s -> s.getStatus() == Subscriber.SubscriberStatus.ACTIVE)
                .toList();

        if (activeSubscribers.isEmpty()) {
            log.debug("No active subscribers for project {}", project.getSlug());
            return;
        }

        String subject = "[" + project.getName() + "] " + post.getTitle();
        String postUrl = publicUrl + "/changelog/" + project.getSlug() + "/" + post.getId();
        String body = buildEmailBody(post, project, postUrl);

        int sent = 0;
        for (Subscriber subscriber : activeSubscribers) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(subscriber.getEmail());
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send notification to {}: {}", subscriber.getEmail(), e.getMessage());
            }
        }

        log.info("Sent notifications to {}/{} subscribers for post '{}'", sent, activeSubscribers.size(), post.getTitle());
    }

    private String buildEmailBody(Post post, Project project, String postUrl) {
        StringBuilder body = new StringBuilder();
        body.append(post.getTitle()).append("\n\n");
        if (post.getSummary() != null && !post.getSummary().isBlank()) {
            body.append(post.getSummary()).append("\n\n");
        }
        body.append("Read the full update: ").append(postUrl).append("\n\n");
        body.append("---\n");
        body.append("You're receiving this because you subscribed to ").append(project.getName()).append(" updates.\n");
        body.append("Unsubscribe: ").append(publicUrl).append("/changelog/").append(project.getSlug()).append("/unsubscribe");
        return body.toString();
    }
}
```

---

## Task 5: Update PostService to Send Notifications on Publish

**File:** `src/main/java/com/changelog/service/PostService.java`

1. Add `@Lazy` import: `import org.springframework.context.annotation.Lazy;`
2. Change the class from `@RequiredArgsConstructor` to manual constructor injection OR add a field with `@Lazy`:

Add this field:
```java
@Lazy
private final SubscriberNotificationService subscriberNotificationService;
```

Since `@RequiredArgsConstructor` with `@Lazy` on a field doesn't work directly, use constructor injection manually or use `@Autowired @Lazy`:

Replace the class-level `@RequiredArgsConstructor` approach by adding:
```java
private final SubscriberNotificationService subscriberNotificationService;
```

And add a custom constructor (or use `@Autowired` with `@Lazy` on the field — but Lombok's `@RequiredArgsConstructor` won't honor `@Lazy`). The cleanest approach: **remove `@RequiredArgsConstructor`** and write the constructor manually, injecting `subscriberNotificationService` with `@Lazy`. Or alternatively, keep `@RequiredArgsConstructor` and inject via setter:

```java
// Add this setter (doesn't conflict with @RequiredArgsConstructor)
@Autowired
public void setSubscriberNotificationService(
        @Lazy SubscriberNotificationService subscriberNotificationService) {
    this.subscriberNotificationService = subscriberNotificationService;
}
```

And change the field to non-final:
```java
private SubscriberNotificationService subscriberNotificationService;
```

3. In `publishPost()`, after `postRepository.save(updated)`:
```java
Post updated = postRepository.save(post);

// Notify subscribers asynchronously (catches its own exceptions)
try {
    subscriberNotificationService.notifySubscribers(updated);
} catch (Exception e) {
    log.error("Failed to trigger subscriber notifications for post {}: {}", postId, e.getMessage());
}

return PostResponse.fromEntity(updated);
```

Add `@Slf4j` to PostService if not already present.

---

## Task 6: Create ScheduledPublishingJob

**New file:** `src/main/java/com/changelog/service/ScheduledPublishingJob.java`

```java
package com.changelog.service;

import com.changelog.model.Post;
import com.changelog.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledPublishingJob {

    private final PostRepository postRepository;
    private final SubscriberNotificationService subscriberNotificationService;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void publishScheduledPosts() {
        List<Post> due = postRepository.findScheduledPostsDueForPublishing(LocalDateTime.now());

        if (due.isEmpty()) {
            return;
        }

        log.info("ScheduledPublishingJob: found {} posts due for publishing", due.size());

        for (Post post : due) {
            try {
                post.setStatus(Post.PostStatus.PUBLISHED);
                post.setPublishedAt(LocalDateTime.now());
                Post saved = postRepository.save(post);
                log.info("Published scheduled post: id={} title='{}' project={}", saved.getId(), saved.getTitle(), saved.getProjectId());

                try {
                    subscriberNotificationService.notifySubscribers(saved);
                } catch (Exception e) {
                    log.error("Notification failed for post {}: {}", saved.getId(), e.getMessage());
                }
            } catch (Exception e) {
                log.error("Failed to publish scheduled post {}: {}", post.getId(), e.getMessage());
            }
        }
    }
}
```

---

## Task 7: Create RssFeedController

**New file:** `src/main/java/com/changelog/controller/RssFeedController.java`

```java
package com.changelog.controller;

import com.changelog.dto.ProjectResponse;
import com.changelog.model.Post;
import com.changelog.repository.PostRepository;
import com.changelog.service.ProjectService;
import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/changelog")
@RequiredArgsConstructor
public class RssFeedController {

    private final ProjectService projectService;
    private final PostRepository postRepository;

    @Value("${app.public-url:http://localhost:8080}")
    private String publicUrl;

    @GetMapping(value = "/{slug}/feed.xml", produces = "application/rss+xml;charset=UTF-8")
    public ResponseEntity<String> getRssFeed(@PathVariable String slug) {
        ProjectResponse project;
        try {
            project = projectService.getProjectBySlug(slug);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        List<Post> posts = postRepository.findByProjectIdAndStatusOrderByPublishedAtDesc(
                project.getId(), Post.PostStatus.PUBLISHED);

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setTitle(project.getName() + " Changelog");
        feed.setDescription(project.getDescription() != null ? project.getDescription() : project.getName() + " release notes");
        feed.setLink(publicUrl + "/changelog/" + slug);

        List<SyndEntry> entries = new ArrayList<>();
        for (Post post : posts) {
            SyndEntry entry = new SyndEntryImpl();
            entry.setTitle(post.getTitle());
            entry.setLink(publicUrl + "/changelog/" + slug + "/" + post.getId());
            entry.setUri(post.getId().toString());

            if (post.getPublishedAt() != null) {
                entry.setPublishedDate(Date.from(post.getPublishedAt().toInstant(ZoneOffset.UTC)));
            }

            SyndContent description = new SyndContentImpl();
            description.setType("text/plain");
            String summary = post.getSummary() != null ? post.getSummary() : truncate(post.getContent(), 200);
            description.setValue(summary);
            entry.setDescription(description);

            entries.add(entry);
        }
        feed.setEntries(entries);

        try {
            String xml = new SyndFeedOutput().outputString(feed);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/rss+xml;charset=UTF-8"))
                    .body(xml);
        } catch (FeedException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
```

---

## Task 8: Add Subscribe/Unsubscribe to PublicChangelogController

**File:** `src/main/java/com/changelog/controller/PublicChangelogController.java`

Add to imports:
```java
import com.changelog.dto.SubscribeRequest;
import com.changelog.model.Subscriber;
import com.changelog.repository.SubscriberRepository;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Optional;
```

Add to constructor (already `@RequiredArgsConstructor`):
```java
private final SubscriberRepository subscriberRepository;
```

Add these two endpoints:

```java
@PostMapping("/{slug}/subscribe")
public ResponseEntity<Void> subscribe(
        @PathVariable String slug,
        @Valid @RequestBody SubscribeRequest request) {

    ProjectResponse project;
    try {
        project = projectService.getProjectBySlug(slug);
    } catch (jakarta.persistence.EntityNotFoundException e) {
        return ResponseEntity.notFound().build();
    }

    Optional<Subscriber> existing = subscriberRepository.findByProjectIdAndEmail(project.getId(), request.getEmail());

    if (existing.isPresent()) {
        Subscriber sub = existing.get();
        if (sub.getStatus() == Subscriber.SubscriberStatus.ACTIVE) {
            return ResponseEntity.status(409).build();
        }
        // Re-activate unsubscribed user
        sub.setStatus(Subscriber.SubscriberStatus.ACTIVE);
        sub.setUnsubscribedAt(null);
        subscriberRepository.save(sub);
        return ResponseEntity.ok().build();
    }

    Subscriber subscriber = Subscriber.builder()
            .projectId(project.getId())
            .email(request.getEmail())
            .name(request.getName())
            .status(Subscriber.SubscriberStatus.ACTIVE)
            .build();
    subscriberRepository.save(subscriber);
    return ResponseEntity.ok().build();
}

@PostMapping("/{slug}/unsubscribe")
public ResponseEntity<Void> unsubscribe(
        @PathVariable String slug,
        @RequestBody SubscribeRequest request) {

    ProjectResponse project;
    try {
        project = projectService.getProjectBySlug(slug);
    } catch (jakarta.persistence.EntityNotFoundException e) {
        return ResponseEntity.ok().build(); // Always 200
    }

    subscriberRepository.findByProjectIdAndEmail(project.getId(), request.getEmail())
            .ifPresent(sub -> {
                sub.setStatus(Subscriber.SubscriberStatus.UNSUBSCRIBED);
                sub.setUnsubscribedAt(LocalDateTime.now());
                subscriberRepository.save(sub);
            });

    return ResponseEntity.ok().build(); // Always 200 (prevents email enumeration)
}
```

---

## Verification Steps

After all code is written, run:

```bash
cd changelog-platform
mvn compile -q
```

Must exit 0 with no errors.

Then:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local &
sleep 15

# Test RSS feed
curl http://localhost:8081/changelog/demo-project/feed.xml

# Test subscribe
curl -X POST http://localhost:8081/changelog/demo-project/subscribe \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","name":"Test User"}'

# Test unsubscribe
curl -X POST http://localhost:8081/changelog/demo-project/unsubscribe \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}'

# Create and publish a post — should trigger subscriber notification (logged)
curl -X POST http://localhost:8081/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{"name":"Jules Test","slug":"jules-test","description":"Test"}'
# Use the returned projectId:
curl -X POST http://localhost:8081/api/v1/projects/{projectId}/posts \
  -H "Content-Type: application/json" \
  -d '{"title":"Jules Feature","summary":"Email + RSS + Scheduled publishing","content":"Built by Jules"}'
# Use the returned postId:
curl -X POST http://localhost:8081/api/v1/posts/{postId}/publish
# Should see log: "Sent notifications to 0/0 subscribers" (no subscribers yet)
```

All curl commands must return non-5xx responses.

---

## Summary of Files to Create/Modify

| Action | File |
|--------|------|
| CREATE | `src/main/java/com/changelog/dto/SubscribeRequest.java` |
| CREATE | `src/main/java/com/changelog/service/SubscriberNotificationService.java` |
| CREATE | `src/main/java/com/changelog/service/ScheduledPublishingJob.java` |
| CREATE | `src/main/java/com/changelog/controller/RssFeedController.java` |
| MODIFY | `pom.xml` — add rome dependency |
| MODIFY | `src/main/resources/application.yml` — add mail + app config |
| MODIFY | `src/main/resources/application-local.yml` — add local mail config |
| MODIFY | `src/main/java/com/changelog/service/PostService.java` — call notifySubscribers on publish |
| MODIFY | `src/main/java/com/changelog/controller/PublicChangelogController.java` — add subscribe/unsubscribe |
