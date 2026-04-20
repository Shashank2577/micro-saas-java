package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.dto.*;
import com.changelog.service.PostService;
import com.changelog.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApiV1Controller {

    private final ProjectService projectService;
    private final PostService postService;
    private final TenantResolver tenantResolver;

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectResponse>> getProjects(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(projectService.getAllProjects(tenantId));
    }

    @PostMapping("/projects")
    public ResponseEntity<ProjectResponse> createProject(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProjectRequest request) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(projectService.createProject(tenantId, request));
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(projectService.getProject(tenantId, projectId));
    }

    @PutMapping("/projects/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectRequest request) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(projectService.updateProject(tenantId, projectId, request));
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        projectService.deleteProject(tenantId, projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/posts")
    public ResponseEntity<List<PostResponse>> getPosts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(postService.getPosts(tenantId, projectId));
    }

    @PostMapping("/projects/{projectId}/posts")
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreatePostRequest request) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        UUID authorId = tenantResolver.getUserId(jwt);
        return ResponseEntity.ok(postService.createPost(tenantId, projectId, authorId, request));
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(postService.getPost(tenantId, postId));
    }

    @PutMapping("/posts/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId,
            @Valid @RequestBody CreatePostRequest request) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(postService.updatePost(tenantId, postId, request));
    }

    @PostMapping("/posts/{postId}/publish")
    public ResponseEntity<PostResponse> publishPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(postService.publishPost(tenantId, postId));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        postService.deletePost(tenantId, postId);
        return ResponseEntity.noContent().build();
    }
}
