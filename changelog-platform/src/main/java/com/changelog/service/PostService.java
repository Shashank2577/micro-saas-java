package com.changelog.service;

import com.changelog.dto.CreatePostRequest;
import com.changelog.dto.PostResponse;
import com.changelog.model.Post;
import com.changelog.model.Tag;
import com.changelog.repository.PostRepository;
import com.changelog.repository.ProjectRepository;
import com.changelog.repository.TagRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final ProjectRepository projectRepository;
    private final TagRepository tagRepository;

    private SubscriberNotificationService subscriberNotificationService;

    @Autowired
    public void setSubscriberNotificationService(
            @Lazy SubscriberNotificationService subscriberNotificationService) {
        this.subscriberNotificationService = subscriberNotificationService;
    }

    public List<PostResponse> getPosts(UUID tenantId, UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project not found");
        }

        return postRepository.findByProjectIdOrderByPublishedAtDesc(projectId).stream()
                .map(PostResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<PostResponse> getPublishedPosts(UUID projectId) {
        return postRepository.findPublishedPosts(projectId).stream()
                .map(PostResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public PostResponse getPost(UUID tenantId, UUID postId) {
        Post post = postRepository.findByIdWithTags(tenantId, postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

        if (!post.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }

        return PostResponse.fromEntity(post);
    }

    @Transactional
    public PostResponse createPost(UUID tenantId, UUID projectId, UUID authorId, CreatePostRequest request) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        if (!project.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }

        Post post = Post.builder()
                .projectId(projectId)
                .tenantId(tenantId)
                .title(request.getTitle())
                .summary(request.getSummary())
                .content(request.getContent())
                .status(request.getStatus() != null ? request.getStatus() : Post.PostStatus.DRAFT)
                .scheduledFor(request.getScheduledFor())
                .authorId(authorId)
                .tags(new HashSet<>())
                .build();

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            post.setTags(new HashSet<>(tags));
        }

        if (post.getStatus() == Post.PostStatus.PUBLISHED) {
            post.setPublishedAt(LocalDateTime.now());
        }

        Post saved = postRepository.save(post);
        return PostResponse.fromEntity(saved);
    }

    @Transactional
    public PostResponse updatePost(UUID tenantId, UUID postId, CreatePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

        if (!post.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }

        post.setTitle(request.getTitle());
        post.setSummary(request.getSummary());
        post.setContent(request.getContent());

        if (request.getStatus() != null && post.getStatus() != request.getStatus()) {
            post.setStatus(request.getStatus());
            if (request.getStatus() == Post.PostStatus.PUBLISHED && post.getPublishedAt() == null) {
                post.setPublishedAt(LocalDateTime.now());
            }
        }

        if (request.getScheduledFor() != null) {
            post.setScheduledFor(request.getScheduledFor());
        }

        if (request.getTagIds() != null) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            post.setTags(new HashSet<>(tags));
        }

        Post updated = postRepository.save(post);
        return PostResponse.fromEntity(updated);
    }

    @Transactional
    public PostResponse publishPost(UUID tenantId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

        if (!post.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }

        post.setStatus(Post.PostStatus.PUBLISHED);
        post.setPublishedAt(LocalDateTime.now());

        Post updated = postRepository.save(post);

        // Notify subscribers asynchronously (catches its own exceptions)
        try {
            subscriberNotificationService.notifySubscribers(updated);
        } catch (Exception e) {
            log.error("Failed to trigger subscriber notifications for post {}: {}", postId, e.getMessage());
        }

        return PostResponse.fromEntity(updated);
    }

    @Transactional
    public void deletePost(UUID tenantId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

        if (!post.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }

        postRepository.delete(post);
    }

    @Transactional
    public void incrementViewCount(UUID postId) {
        postRepository.findById(postId).ifPresent(post -> {
            post.setViewCount((post.getViewCount() != null ? post.getViewCount() : 0) + 1);
            postRepository.save(post);
        });
    }
}
