package com.changelog.controller;

import com.changelog.model.Board;
import com.changelog.model.FeedbackPost;
import com.changelog.repository.LocalTenantRepository;
import com.changelog.service.BoardService;
import com.changelog.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.changelog.exception.EntityNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/public/{tenantSlug}")
@RequiredArgsConstructor
public class PublicFeedbackController {

    private final BoardService boardService;
    private final PostService postService;
    // We would typically use a tenant service to resolve slug -> ID, assuming cc.tenants is accessible
    // For MVP, if we don't have TenantRepository in this module, we should create a lightweight one.
    // Or we assume the frontend passes the tenantId in some way.
    // Let's create a minimal Tenant lookup via a local repo for the cross-cutting schema.
    private final com.changelog.repository.LocalTenantRepository tenantRepository;

    private UUID resolveTenantId(String slug) {
        return tenantRepository.findBySlug(slug)
                .map(com.changelog.model.cc.Tenant::getId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
    }

    @GetMapping("/boards/{boardSlug}/posts")
    public ResponseEntity<List<FeedbackPost>> getPublicPosts(@PathVariable String tenantSlug, @PathVariable String boardSlug) {
        UUID tenantId = resolveTenantId(tenantSlug);
        Board board = boardService.getBoardBySlug(tenantId, boardSlug);
        return ResponseEntity.ok(postService.getPublicPostsByBoard(board.getId(), tenantId));
    }

    @PostMapping("/boards/{boardSlug}/posts")
    public ResponseEntity<FeedbackPost> submitPublicPost(@PathVariable String tenantSlug, @PathVariable String boardSlug, @RequestBody FeedbackPost post) {
        UUID tenantId = resolveTenantId(tenantSlug);
        Board board = boardService.getBoardBySlug(tenantId, boardSlug);
        post.setPublic(true);
        return ResponseEntity.ok(postService.createPost(board.getId(), tenantId, post));
    }

    @PostMapping("/posts/{postId}/vote")
    public ResponseEntity<Void> votePost(@PathVariable String tenantSlug, @PathVariable UUID postId, @RequestBody Map<String, String> payload) {
        UUID tenantId = resolveTenantId(tenantSlug);
        String email = payload.get("email");
        String name = payload.get("name");
        postService.voteOnPost(postId, tenantId, email, name);
        return ResponseEntity.ok().build();
    }
}
