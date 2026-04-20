package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.model.FeedbackPost;
import com.changelog.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/boards/{boardId}/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<FeedbackPost>> getPosts(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID boardId) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(postService.getPostsByBoard(boardId, tenantId));
    }

    @PostMapping
    public ResponseEntity<FeedbackPost> createPost(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID boardId, @RequestBody FeedbackPost post) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        post.setSubmittedBy(tenantResolver.getUserId(jwt));
        return ResponseEntity.ok(postService.createPost(boardId, tenantId, post));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<FeedbackPost> getPost(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID boardId, @PathVariable UUID postId) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(postService.getPost(postId, tenantId));
    }

    @PutMapping("/{postId}/status")
    public ResponseEntity<FeedbackPost> updatePostStatus(@AuthenticationPrincipal Jwt jwt, 
                                                         @PathVariable UUID boardId, 
                                                         @PathVariable UUID postId, 
                                                         @RequestBody Map<String, String> payload) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        UUID userId = tenantResolver.getUserId(jwt);
        String newStatus = payload.get("status");
        String note = payload.get("note");
        return ResponseEntity.ok(postService.updatePostStatus(postId, tenantId, newStatus, userId, note));
    }
}
