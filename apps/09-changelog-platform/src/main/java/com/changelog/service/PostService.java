package com.changelog.service;

import com.changelog.dto.CreatePostRequest;
import com.changelog.dto.PostMapper;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final PostMapper postMapper;

    private SubscriberNotificationService subscriberNotificationService;

    @Autowired
    public void setSubscriberNotificationService(
            @Lazy SubscriberNotificationService subscriberNotificationService) {
        this.subscriberNotificationService = subscriberNotificationService;
    }

    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    public List<PostResponse> getPosts(UUID tenantId, UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project not found");
        }

        return postRepository.findByProjectIdOrderByPublishedAtDesc(projectId).stream()
                .map(postMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<PostResponse> getPublishedPosts(UUID projectId) {
        return postRepository.findPublishedPosts(projectId).stream()
                .map(postMapper::toResponse)
                .collect(Collectors.toList());
    }

    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    public PostResponse getPost(UUID tenantId, UUID postId) {
        Post post = postRepository.findByIdWithTags(tenantId, postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

        return postMapper.toResponse(post);
    }

    @Transactional
    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    @CacheEvict(value = {"changelogs", "changelog_posts"}, allEntries = true)
    public PostResponse createPost(UUID tenantId, UUID projectId, UUID authorId, CreatePostRequest request) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        if (!project.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }

        Post post = postMapper.toEntity(request);
        post.setProjectId(projectId);
        post.setTenantId(tenantId);
        post.setAuthorId(authorId);
        post.setViewCount(0);
        post.setTags(new HashSet<>());

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            post.setTags(new HashSet<>(tags));
        }

        if (post.getStatus() == Post.PostStatus.PUBLISHED) {
            post.setPublishedAt(LocalDateTime.now());
        }

        Post saved = postRepository.save(post);
        return postMapper.toResponse(saved);
    }

    @Transactional
    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    @CacheEvict(value = {"changelogs", "changelog_posts", "posts"}, allEntries = true)
    public PostResponse updatePost(UUID tenantId, UUID postId, CreatePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

        postMapper.updateEntity(request, post);

        if (request.getStatus() == Post.PostStatus.PUBLISHED && post.getPublishedAt() == null) {
            post.setPublishedAt(LocalDateTime.now());
        }

        if (request.getTagIds() != null) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            post.setTags(new HashSet<>(tags));
        }

        Post updated = postRepository.save(post);
        return postMapper.toResponse(updated);
    }

    @Transactional
    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    @CacheEvict(value = {"changelogs", "changelog_posts", "posts"}, allEntries = true)
    public PostResponse publishPost(UUID tenantId, UUID postId) {
        Post post = postRepository.findByIdWithTags(tenantId, postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

        post.setStatus(Post.PostStatus.PUBLISHED);
        post.setPublishedAt(LocalDateTime.now());

        Post updated = postRepository.save(post);

        try {
            subscriberNotificationService.notifySubscribers(updated);
        } catch (Exception e) {
            log.error("Failed to trigger subscriber notifications for post {}: {}", postId, e.getMessage());
        }

        return postMapper.toResponse(updated);
    }

    @Transactional
    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    @CacheEvict(value = {"changelogs", "changelog_posts", "posts"}, allEntries = true)
    public void deletePost(UUID tenantId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

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
