package com.changelog.controller;

import com.changelog.dto.PostResponse;
import com.changelog.dto.ProjectResponse;
import com.changelog.dto.PublicChangelogResponse;
import com.changelog.dto.SubscribeRequest;
import com.changelog.model.Post;
import com.changelog.model.Subscriber;
import com.changelog.repository.PostRepository;
import com.changelog.repository.SubscriberRepository;
import com.changelog.service.PostService;
import com.changelog.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/changelog")
@RequiredArgsConstructor
public class PublicChangelogController {

    private final ProjectService projectService;
    private final PostService postService;
    private final PostRepository postRepository;
    private final SubscriberRepository subscriberRepository;

    @GetMapping("/{slug}")
    public ResponseEntity<PublicChangelogResponse> getChangelog(@PathVariable String slug) {
        ProjectResponse project;
        try {
            project = projectService.getProjectBySlug(slug);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        List<PostResponse> posts = postRepository
                .findByProjectIdAndStatusOrderByPublishedAtDesc(project.getId(), Post.PostStatus.PUBLISHED)
                .stream()
                .map(PostResponse::fromEntity)
                .toList();

        PublicChangelogResponse response = PublicChangelogResponse.builder()
                .projectName(project.getName())
                .slug(project.getSlug())
                .description(project.getDescription())
                .branding(project.getBranding())
                .posts(posts)
                .totalPosts(posts.size())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{slug}/posts")
    public ResponseEntity<List<PostResponse>> getChangelogPosts(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProjectResponse project;
        try {
            project = projectService.getProjectBySlug(slug);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        List<PostResponse> posts = postRepository
                .findByProjectIdAndStatus(
                        project.getId(),
                        Post.PostStatus.PUBLISHED,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt")))
                .stream()
                .map(PostResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(posts);
    }

    @GetMapping("/{slug}/posts/{postId}")
    public ResponseEntity<PostResponse> getChangelogPost(
            @PathVariable String slug,
            @PathVariable UUID postId) {
        ProjectResponse project;
        try {
            project = projectService.getProjectBySlug(slug);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        PostResponse post;
        try {
            post = postService.getPost(project.getTenantId(), postId);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        if (!Post.PostStatus.PUBLISHED.equals(post.getStatus())) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(post);
    }

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

        String normalizedEmail = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        Optional<Subscriber> existing = subscriberRepository.findByProjectIdAndEmail(project.getId(), normalizedEmail);

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
                .email(normalizedEmail)
                .name(request.getName())
                .status(Subscriber.SubscriberStatus.ACTIVE)
                .build();
        subscriberRepository.save(subscriber);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{slug}/unsubscribe")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable String slug,
            @Valid @RequestBody SubscribeRequest request) {

        ProjectResponse project;
        try {
            project = projectService.getProjectBySlug(slug);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.ok().build(); // Always 200
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        subscriberRepository.findByProjectIdAndEmail(project.getId(), normalizedEmail)
                .ifPresent(sub -> {
                    sub.setStatus(Subscriber.SubscriberStatus.UNSUBSCRIBED);
                    sub.setUnsubscribedAt(LocalDateTime.now());
                    subscriberRepository.save(sub);
                });

        return ResponseEntity.ok().build(); // Always 200 (prevents email enumeration)
    }
}
